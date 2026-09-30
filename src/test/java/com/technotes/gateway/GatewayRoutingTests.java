package com.technotes.gateway;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false")
class GatewayRoutingTests {

    private static final DisposableServer NOTES = backend("notes");
    private static final DisposableServer USERS = backend("users");

    @LocalServerPort
    private int port;

    private static DisposableServer backend(String name) {
        return HttpServer.create().host("127.0.0.1").port(0)
                .handle((request, response) -> {
                    response.header("X-Backend", name)
                            .header("X-Received-Uri", request.uri())
                            .header("X-Received-Method", request.method().name());
                    if (request.requestHeaders().contains(HttpHeaders.ORIGIN)) {
                        // Match the real OAuth service, which also writes CORS headers.
                        response.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");
                    }
                    String token = request.requestHeaders().get(HttpHeaders.AUTHORIZATION);
                    if (token != null) {
                        response.header("X-Received-Authorization", token);
                    }
                    String version = request.requestHeaders().get(HttpHeaders.IF_MATCH);
                    if (version != null) {
                        response.header(HttpHeaders.ETAG, version);
                    }
                    if ("users".equals(name) && token == null) {
                        return response.status(401).sendString(Mono.just("unauthorized"));
                    }
                    return response.send(request.receive().retain());
                }).bindNow();
    }

    @DynamicPropertySource
    static void destinations(DynamicPropertyRegistry registry) {
        GatewayTestEnvironment.register(registry);
        registry.add("NOTES_SERVICE_URL", () -> "http://127.0.0.1:" + NOTES.port());
        registry.add("USER_OAUTH_SERVICE_URL", () -> "http://127.0.0.1:" + USERS.port());
    }

    @AfterAll
    static void stopBackends() {
        NOTES.disposeNow();
        USERS.disposeNow();
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10)).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/notes", "/api/v1/notes/123",
            "/api/v1/public/categories", "/api/v1/public/notes", "/api/v1/public/notes/java"})
    void forwardsNotesPathsAndQueries(String path) {
        client().get().uri(path + "?page=0&size=10").exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Backend", "notes")
                .expectHeader().valueEquals("X-Received-Uri", path + "?page=0&size=10");
    }

    @Test
    void preservesWriteBodyBearerTokenAndVersion() {
        client().patch().uri("/api/v1/notes/123")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-access-token")
                .header(HttpHeaders.IF_MATCH, "\"note-123-v1\"")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{\"title\":\"Updated\"}").exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Received-Method", "PATCH")
                .expectHeader().valueEquals("X-Received-Authorization", "Bearer test-access-token")
                .expectHeader().valueEquals(HttpHeaders.ETAG, "\"note-123-v1\"")
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "ETag, Location")
                .expectBody(String.class).isEqualTo("{\"title\":\"Updated\"}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/categories", "/api/v1/notes", "/api/v1/notes/123/submit",
            "/api/v1/notes/123/publish"})
    void forwardsNotesPostRequests(String path) {
        client().post().uri(path).exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Backend", "notes")
                .expectHeader().valueEquals("X-Received-Method", "POST");
    }

    @Test
    void forwardsProfileBearerTokenAndDownstreamUnauthorizedStatus() {
        client().get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-access-token")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Backend", "users")
                .expectHeader().valueEquals("X-Received-Uri", "/api/v1/users/me")
                .expectHeader().valueEquals("X-Received-Authorization", "Bearer test-access-token");
        client().get().uri("/api/v1/users/me").exchange().expectStatus().isUnauthorized()
                .expectBody(String.class).isEqualTo("unauthorized");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/oauth2/authorize", "/oauth2/token", "/oauth2/jwks", "/login",
            "/.well-known/openid-configuration", "/api/v1/users", "/api/v1/users/123",
            "/api/v1/users/me/settings", "/api/v1/public/unrelated", "/api/v1/unrelated"})
    void doesNotRouteUnrelatedOrOAuthEndpoints(String path) {
        client().get().uri(path).exchange().expectStatus().isNotFound()
                .expectHeader().doesNotExist("X-Backend");
    }

    @Test
    void profileRouteIsReadOnly() {
        client().post().uri("/api/v1/users/me").exchange().expectStatus().isNotFound()
                .expectHeader().doesNotExist("X-Backend");
    }

    @Test
    void allowsUiPreflightAndRejectsUnconfiguredOrigin() {
        client().options().uri("/api/v1/users/me")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");
        client().options().uri("/api/v1/notes/123")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,if-match")
                .exchange().expectStatus().isOk();
        client().options().uri("/api/v1/users/me")
                .header(HttpHeaders.ORIGIN, "http://untrusted.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange().expectStatus().isForbidden();
    }
}
