# API Gateway Service

> Status: design and implementation notes for the first slice. This documentation change does not implement or certify the routes below; verify the existing `develop` source and dependencies before coding them.

## Purpose and ownership

Repository: `technotes-api-gateway`  
Runtime: Java 21, Spring Boot 3.5.16, Spring Cloud 2025.0.3  
Port: 8080  
Owner: Platform/API edge; Notes Service owns note data and business rules.

The planned Gateway is the browser and client entry point. It matches supported request paths, discovers the Notes Service through Eureka and forwards requests using Spring Cloud LoadBalancer.

## Local request path

```text
React/browser or API client
  -> http://localhost:8080/api/v1/notes/**
  -> API Gateway (WebFlux)
  -> Eureka service ID: technotes-notes-service
  -> Notes Service on port 8081
  -> MongoDB database technotes_notes_db
```

The Gateway must not read or write the Notes MongoDB database.

## Stack and configuration

Target the reactive Spring Cloud Gateway starter `spring-cloud-starter-gateway-server-webflux`. Do not combine it with the MVC Gateway starter or `spring-boot-starter-web`. Notes Service remains Spring MVC; the services do not need to use the same web stack.

Target local configuration: port 8080, Eureka at `http://localhost:8761/eureka/`, and an optional Config Server import at `http://localhost:8888`. Route configuration is in `src/main/resources/application-local.yml` under `spring.cloud.gateway.server.webflux.routes`.

## Initial routes

| Route ID | Incoming path | Destination | Behavior |
|---|---|---|---|
| `notes-api` | `/api/v1/notes/**` | `lb://technotes-notes-service` | Preserve the path |
| `notes-api` | `/api/v1/categories/**` | `lb://technotes-notes-service` | Preserve the path |
| `notes-api` | `/api/v1/topics/**` | `lb://technotes-notes-service` | Preserve the path |

A 404 from Notes means the route reached an application that does not yet implement that API. A 503 usually means there is no healthy, registered destination. Check Eureka and service logs before changing route predicates.

## Local run and checks

Start Eureka, Config Server, Notes Service, then Gateway. Build with `./mvnw clean verify` (Windows: `mvnw.cmd clean verify`). Check `http://localhost:8080/actuator/health` for local health once Actuator is configured.

For the implementation, add a Gateway test that starts a controlled HTTP upstream, registers it with Spring Cloud's simple discovery client and verifies path-preserving load-balanced routing plus 404 for an unknown path. It does not require the real Eureka server.

## Security boundary

JWT validation, role checks, rate limits and production ingress are not implemented by this baseline. Do not treat the Gateway as secure because it is the single entry point. Before any public deployment, authenticate requests and enforce authorization in Notes Service for every protected action. Never expose draft notes publicly. Keep actuator endpoints and service ports private.

## First implementation slice

Route the draft-note API only after the Notes API contract is agreed. Add gateway filters after their behavior is specified and tested; do not trust a caller-supplied identity or secret header.

## Related decisions

- Platform and local setup: `technotes-documentation/architecture/ADR-001-platform-baseline.md` and `development/local-setup.md`.
- Service implementation: `technotes-notes-service/doc/notes-service-overview.md`.
- Open decisions: confirm note taxonomy and content type model in the first Notes domain ticket.
