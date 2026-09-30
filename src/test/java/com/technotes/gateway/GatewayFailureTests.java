package com.technotes.gateway;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayFailureTests {
    private static final DisposableServer NOTES = HttpServer.create().host("127.0.0.1").port(0)
            .handle((request, response) -> {
                if (request.uri().contains("delay=true")) {
                    return response.sendString(Mono.delay(Duration.ofSeconds(3)).map(value -> "late"));
                }
                if (request.uri().contains("status=")) {
                    int status = Integer.parseInt(request.uri().split("status=")[1]);
                    return response.status(status).header(HttpHeaders.CONTENT_TYPE, "application/json")
                            .sendString(Mono.just("{\"code\":\"DOWNSTREAM_ERROR\"}"));
                }
                return response.status(201).header(HttpHeaders.LOCATION, "/api/v1/notes/test-id")
                        .header(HttpHeaders.ETAG, "\"note-test-id-v0\"")
                        .sendString(Mono.just("{\"id\":\"test-id\"}"));
            }).bindNow();
    private static final int UNAVAILABLE_PORT = closedPort();

    @LocalServerPort
    private int port;

    private static int closedPort() {
        DisposableServer server = HttpServer.create().host("127.0.0.1").port(0).bindNow();
        int port = server.port();
        server.disposeNow();
        return port;
    }

    @DynamicPropertySource
    static void destinations(DynamicPropertyRegistry registry) {
        GatewayTestEnvironment.register(registry);
        registry.add("NOTES_SERVICE_URL", () -> "http://127.0.0.1:" + NOTES.port());
        registry.add("USER_OAUTH_SERVICE_URL", () -> "http://127.0.0.1:" + UNAVAILABLE_PORT);
        registry.add("GATEWAY_RESPONSE_TIMEOUT", () -> "1s");
    }

    @AfterAll
    static void shutdown() {
        NOTES.disposeNow();
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(5)).build();
    }

    @Test
    void unavailableBackendReturnsContractErrorWithCors() {
        client().get().uri("/api/v1/users/me")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .exchange().expectStatus().isEqualTo(503)
                .expectHeader().contentType("application/json")
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
                .expectBody().jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.path").isEqualTo("/api/v1/users/me")
                .jsonPath("$.timestamp").isNotEmpty()
                .jsonPath("$.traceId").isNotEmpty()
                .jsonPath("$.fieldErrors").isArray()
                .jsonPath("$.message").isEqualTo("Requested service is temporarily unavailable.");
    }

    @Test
    void delayedBackendReturnsContractError() {
        client().get().uri("/api/v1/public/notes?delay=true").exchange()
                .expectStatus().isEqualTo(503).expectBody()
                .jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.path").isEqualTo("/api/v1/public/notes");
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 409, 412, 428, 500, 503, 504})
    void preservesDownstreamErrorStatusAndBody(int status) {
        client().get().uri("/api/v1/public/notes?status=" + status).exchange()
                .expectStatus().isEqualTo(status).expectBody(String.class)
                .isEqualTo("{\"code\":\"DOWNSTREAM_ERROR\"}");
    }

    @Test
    void preservesCreationStatusLocationAndEtag() {
        client().post().uri("/api/v1/notes").exchange().expectStatus().isCreated()
                .expectHeader().valueEquals(HttpHeaders.LOCATION, "/api/v1/notes/test-id")
                .expectHeader().valueEquals(HttpHeaders.ETAG, "\"note-test-id-v0\"");
    }

    @Test
    void excludedMethodsAreNotForwarded() {
        client().delete().uri("/api/v1/notes/test-id").exchange().expectStatus().isNotFound();
        client().get().uri("/api/v1/categories").exchange().expectStatus().isNotFound();
        client().post().uri("/api/v1/public/notes").exchange().expectStatus().isNotFound();
        client().post().uri("/api/v1/notes/test-id/archive").exchange().expectStatus().isNotFound();
    }

    @Test
    void exposesOnlyHealthWithoutDetails() {
        client().get().uri("/actuator/health").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.components").doesNotExist();
        client().get().uri("/actuator/env").exchange().expectStatus().isNotFound();
        client().get().uri("/actuator/gateway/routes").exchange().expectStatus().isNotFound();
    }
}
