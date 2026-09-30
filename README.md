# TechNotes API Gateway

Java 21, Spring Boot 3.5.16 and Spring Cloud 2025.0.3. Gateway port: **8080**.

## Routes

| Gateway path | Destination (default) |
|---|---|
| `/api/v1/categories` and descendants | Notes, `http://localhost:8081` |
| `/api/v1/notes` and descendants | Notes, `http://localhost:8081` |
| `/api/v1/public/categories` | Notes, `http://localhost:8081` |
| `/api/v1/public/notes` and descendants | Notes, `http://localhost:8081` |
| **GET** `/api/v1/users/me` | User OAuth, `http://localhost:9000` |

Paths and query strings are preserved. Request bodies, `Authorization: Bearer ...`
and `If-Match` pass through; downstream responses retain their status, body, ETag
and Location. There is no prefix stripping or token exchange.

The gateway only routes requests. Each backend must validate tokens and enforce
its scopes, roles and ownership rules. Routing does not implement missing backend
endpoints or add security to an unsecured backend.

## OAuth issuer stays unchanged

The existing OAuth service uses `AUTH_ISSUER`, defaulting to
`http://localhost:9000`. Do not change it to the gateway URL.
The browser continues to use port 9000 for `/oauth2/authorize`, `/login`,
`/oauth2/token` and `/oauth2/jwks`. These paths and discovery metadata are not
routed through this gateway. Only the profile business API uses port 8080.

## Local setup in IntelliJ / PowerShell

1. Use JDK 21 and reload the Maven project.
2. Start PostgreSQL and the OAuth service with its existing environment variables
   and issuer on port 9000.
3. Start MongoDB and Notes on **8081**. Its current `develop` configuration does
   not set a port, so add `SERVER_PORT=8081` to the **Notes** IntelliJ run
   configuration (or pass `--server.port=8081`). Otherwise it will conflict with
   the gateway on 8080.
4. Start Eureka on 8761 for the gateway's existing registration, or set
   `EUREKA_CLIENT_ENABLED=false` in the gateway run configuration for standalone
   routing. The backends currently do not configure Eureka clients, so routes
   use direct HTTP destinations. Automatic discovery routes are disabled.
5. Run the gateway:

   ```powershell
   .\mvnw.cmd clean test
   .\mvnw.cmd spring-boot:run
   ```

| Gateway environment variable | Default | Purpose |
|---|---|---|
| `NOTES_SERVICE_URL` | `http://localhost:8081` | Notes origin, without an API path |
| `USER_OAUTH_SERVICE_URL` | `http://localhost:9000` | Profile API destination; does not configure issuer |
| `UI_ORIGIN` | `http://localhost:5173` | Exact browser origin allowed by gateway CORS |

For containers or a remote environment, supply reachable backend origins instead
of localhost. Issuer and token validation remain separately configured in the
OAuth and Notes services.

CORS supports GET, POST, PATCH and OPTIONS, allows Authorization, Content-Type
and If-Match, and exposes ETag and Location. The UI sends bearer tokens without
gateway session cookies. OAuth token-endpoint CORS remains owned by OAuth.

## Smoke checks

```powershell
curl.exe -i http://localhost:8080/actuator/health
curl.exe -i http://localhost:8080/api/v1/public/categories
curl.exe -i "http://localhost:8080/api/v1/public/notes?page=0&size=10"
curl.exe -i http://localhost:8080/api/v1/users/me
curl.exe -i -H "Authorization: Bearer $env:ACCESS_TOKEN" http://localhost:8080/api/v1/users/me
```

With the backends implemented and running, public reads should succeed; `/me`
without a token should return the OAuth service's 401, and a valid access token
with `profile.read` should return the profile. A downstream 404 can mean the
endpoint has not been implemented yet. A connection error means the destination
or backend port needs checking. Eureka availability can affect aggregate health
when its client is enabled.

`GatewayRoutingTests` uses two isolated HTTP stub backends and the real gateway.
It checks destination selection, paths/query strings, write payloads, bearer and
version headers, downstream 401, CORS preflights and excluded endpoints. These
tests do not validate real JWTs or replace the live cross-service smoke checks.

Configuration uses the Gateway 4.3 WebFlux namespace
`spring.cloud.gateway.server.webflux` and its dedicated starter:
[Spring reference](https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/starter.html).
