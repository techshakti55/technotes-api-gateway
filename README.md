# TechNotes API Gateway — first live

Java 21, Spring Boot 3.5.16, Spring Cloud 2025.0.3. Based on the documentation
repository `develop` first-live contracts inspected at `18a62c2`.

## Run on Windows

From the Gateway project directory, create your local configuration once:

```powershell
Copy-Item .env.example .env
notepad .env
.\scripts\gateway.ps1 -Action Test
.\scripts\gateway.ps1 -Action Run
```

`.env.example` contains **local examples**, not production defaults. Edit `.env`
for your environment. The script reads plain `KEY=value` entries without
executing their contents, checks required variables, and runs Maven. No values
in `.env` are committed. Spring Boot does not automatically load `.env`:
IntelliJ users must set the same values in their run configuration, or use this
script. Keep one Gateway process running at a time.

For a release JAR:

```powershell
.\scripts\gateway.ps1 -Action Package
```

The executable JAR is produced under `target/`. Supply the same environment
variables when running `java -jar` in your deployment.

## Required environment

`application.yaml` has no default ports, URLs, UI origins or timeout values.
Missing configuration fails startup. Runtime configuration validates exact
origins (no wildcard, path, embedded credentials, query or fragment) and positive
timeouts. Only test fixtures and the local example file contain sample values.

| Variable | Meaning |
|---|---|
| SERVER_PORT | Gateway listening port |
| NOTES_SERVICE_URL | Reachable Notes HTTP(S) origin, no path/trailing slash |
| USER_OAUTH_SERVICE_URL | Reachable profile-service origin, no path/trailing slash |
| UI_ORIGIN | One exact browser origin, no trailing slash |
| GATEWAY_CONNECT_TIMEOUT_MS | Positive connection timeout in milliseconds |
| GATEWAY_RESPONSE_TIMEOUT | Positive response timeout duration, e.g. `10s` |
| CORS_MAX_AGE_SECONDS | Positive preflight cache duration in seconds |
| EUREKA_CLIENT_ENABLED | Explicit `true`/`false`; direct routes do not need Eureka |
| EUREKA_URL | Discovery URL; required for configuration even when disabled |

For public deployment, terminate HTTPS at the chosen trusted edge, configure
exact HTTPS UI and issuer/callback values in the relevant services, and make
backend origins reachable. Local addresses inside containers refer to those
containers. Keep backend ports private in the deployment network. Do not trust
arbitrary forwarded headers; configure the chosen proxy topology explicitly.
Production DNS/compute/certificate choices are deployment decisions, not defaults
invented here.

## Fixed routes

| Method | Gateway path | Destination |
|---|---|---|
| GET | `/api/v1/public/categories` | Notes |
| GET | `/api/v1/public/notes`, `/api/v1/public/notes/{slug}` | Notes |
| POST | `/api/v1/categories` | Notes |
| GET, POST | `/api/v1/notes` | Notes |
| GET, PATCH | `/api/v1/notes/{id}` | Notes |
| POST | `/api/v1/notes/{id}/submit`, `/api/v1/notes/{id}/publish` | Notes |
| GET | `/api/v1/users/me` | User/OAuth |

Paths, queries, request bodies, Authorization and If-Match pass through.
Downstream HTTP statuses/bodies, ETag and Location are preserved, including
backend errors. Excluded methods and paths are not forwarded. CORS allows only
the configured UI origin and GET/POST/PATCH/OPTIONS, allows Authorization,
Content-Type and If-Match, and exposes ETag/Location. Credentials are disabled
at the Gateway; business calls use bearer tokens.

OAuth `/oauth2/authorize`, `/login`, `/oauth2/token`, `/oauth2/jwks` and discovery
metadata stay at the issuer, not the Gateway. Gateway does not exchange tokens
or issue identities. OAuth and Notes independently validate JWTs and scopes;
Notes owns roles, ownership, privacy and publication rules. CORS is not API
authorization. A downstream `401 Basic` is forwarded until Notes security is
implemented; it is not evidence that the Gateway is a JWT resource server.

## Failure behavior and health

Connection refusal, DNS failure and recognized connection/response timeouts
before response commitment return HTTP 503 with:

```json
{"timestamp":"...","status":503,"code":"SERVICE_UNAVAILABLE","message":"Requested service is temporarily unavailable.","path":"...","traceId":"...","fieldErrors":[]}
```

No internal exception text, backend address or token is put in the JSON. The
traceId is the Gateway request ID. Already-started responses cannot be replaced
with a new JSON response. These timeout settings bound connection establishment
and waiting for response headers; they are not a total streamed-body deadline.
No automatic retry is configured for writes.

Only `/actuator/health` is exposed, with no component details. It describes
Gateway health; it does not prove Notes/OAuth are reachable or secure.

## Verification and remaining release gates

Automated tests use isolated HTTP backends and test-only configuration: fixed
routes/methods, payload/query/header forwarding, creation ETag/Location,
downstream error preservation, CORS (including 503), unavailable and delayed
backends, restricted actuator exposure and configuration validation.

Real-token profile checks already supplied by Shakti: valid token 200;
missing/malformed token 401; local preflight permitted; unconfigured origin
rejected; Notes stopped produced the common 503 response.

Before declaring the full website live, verify separately:

- Notes validates issuer/audience/signature/time/ID-token rejection and scopes,
  roles and ownership; anonymous reads never expose drafts/private content.
- Real Notes create/edit/submit/publish responses and 428/412 pass through.
- Browser OAuth/React integration and production HTTPS/CORS/callback work.
- Persistence, backups, rollback and production network configuration pass.

References: documentation `docs/first-live/README.md`, `notes.md`, `oauth.md`,
`ui-integration.md`. No signup, payment, Kafka or extra business endpoints are
introduced by this change.
