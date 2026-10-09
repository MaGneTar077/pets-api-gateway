# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Reactive API Gateway (Spring Cloud Gateway on WebFlux) for the "MyAnimaLog" pet-management platform. It is the single public entry point that routes requests from the frontend to the backend microservices, and centralizes cross-cutting concerns: JWT authentication, identity propagation, per-user rate limiting, circuit breaking/fallbacks, CORS, and uniform JSON error responses.

## Commands

- Build: `./mvnw clean package` (Windows: `mvnw.cmd clean package`)
- Run locally: `./mvnw spring-boot:run` (reads `.env` via `spring-dotenv`; requires `JWT_SECRET` at minimum — see Configuration below)
- Run tests: `./mvnw test`
- Run a single test: `./mvnw test -Dtest=ApiGatewayApplicationTests#contextLoads`
- Build the Docker image: `docker build -t api-gateway .` (multi-stage Maven build, runs as non-root `appuser`, exposes port 8090)

There is no linter/formatter configured in this project.

## Configuration

All runtime config lives in `src/main/resources/application.yaml` and is overridden via environment variables (loaded from a local `.env` file through `spring-dotenv`, which is gitignored). Key variables:

- `JWT_SECRET` — HMAC signing key for legacy HS256 tokens. Required only while `JWT_ACCEPT_LEGACY=true`; the app fails fast at startup if it's blank in that case.
- `JWT_JWKS_URI` — JWKS endpoint of the token issuer (RS256 public keys), e.g. `.../.well-known/jwks.json` (required, no default).
- `JWT_ISSUER` — expected `iss` claim (default `myanimalog-user-service`).
- `JWT_AUDIENCE` — expected `aud` claim, must be contained in the token's audience list (default `myanimalog-api`).
- `JWT_ACCEPT_LEGACY` — when `true` (default), tokens without a `kid` header are still validated as HS256 with `JWT_SECRET`; set to `false` to reject them outright once no client holds a pre-RS256 token.
- `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_SSL` — backs the rate limiter.
- `FRONTEND_URL` — allowed CORS origin.
- `PORT` — gateway listen port (default 8090); also used as the fallback-route target host.
- `*_SERVICE_URL` — one per downstream service (`USER_SERVICE_URL`, `PET_SERVICE_URL`, `VETERINARY_SERVICE_URL`, `MEDICAL_SERVICE_URL`, `CALENDAR_SERVICE_URL`, `NOTIFICATION_SERVICE_URL`), each defaulting to a `localhost` port for local dev.

## Architecture

### Routing (`application.yaml`)

Routes are declared config-first (no Java `RouteLocator`). Each downstream service gets one or more route entries matching on `Path` predicates. Two route "shapes" exist:

- **Public routes** (`user-register`, `user-auth`): rate-limited only, no JWT filter — used for registration/login endpoints under `/api/user/register` and `/api/auth/**`.
- **Protected routes** (`user-service`, `pet-service`, `veterinary-service`, `medical-pet-service`, `calendar-service`, `notification-service`): add the custom `JwtAuthFilter` before rate limiting, gating everything else (`/api/pets/**`, `/api/veterinary/**`, `/api/visits/**`, `/api/weight/**`, `/api/treatments/**`, `/api/lab/**`, `/api/medications/**`, `/api/surgery/**`, `/api/vaccines/**`, `/api/calendar-events/**`, `/api/notifications/**`, remaining `/api/user/**`).

Most routes use `RewritePath=/api/(?<segment>.*), /${segment}` to strip the `/api` prefix before forwarding (the medical and calendar services receive the full path unchanged — check this when adding new routes to those services).

Every protected/public route also wires a `CircuitBreaker` (Resilience4j) pointing at a `forward:/fallback/<service>` URI, and those fallback paths are themselves declared as routes pointing back at the gateway's own port so the forward resolves. New downstream service → add both the real route and a matching `fallback-<service>` route, plus a `resilience4j.circuitbreaker.instances.<name>` / `timelimiter.instances.<name>` entry.

### Request pipeline (order matters)

1. **CORS** (`globalcors` in `application.yaml`) — origin from `FRONTEND_URL`, credentials allowed.
2. **`StripIdentityHeadersGlobalFilter`** (`filter/StripIdentityHeadersGlobalFilter.java`, `Ordered.HIGHEST_PRECEDENCE`) — strips any client-supplied `X-User-Id`/`X-User-Email` headers on *every* request before anything else runs, so a caller can never spoof identity.
3. **`JwtAuthFilter`** (`filter/JwtAuthFilter.java`, applied per-route) — validates the `Authorization: Bearer <token>` header via `JwtUtil.validate(token)` (reactive: `Mono<Map<String,Object>>`), extracts `userId`/`id` and `email` claims (falls back to the `sub` claim, distinguishing it as an id vs. an email by whether it contains `@`), and re-sets `X-User-Id` / `X-User-Email` on the outgoing request. Downstream services trust these headers as the authenticated identity — they must never be reachable directly, only through the gateway.
4. **`RequestRateLimiter`** — Redis-backed token bucket per route (`redis-rate-limiter.*` args), keyed by `userKeyResolver` (`ratelimit/RateLimitConfig.java`), which decodes the JWT payload itself (without verifying the signature — it's only used for bucketing) to key on `id`, falling back to the caller's remote IP when there's no valid token. This works unmodified for both token formats since it never checks the signature.
5. **`CircuitBreaker`** — Resilience4j, routes to the matching `/fallback/*` endpoint in `FallbackController` on open circuit.

`JwtUtil` (`util/JwtUtil.java`) validates two token formats, selected by whether the JWT header carries a `kid`:
- **RS256 (current format, has `kid`)** — verified via a `NimbusReactiveJwtDecoder` pointed at `JWT_JWKS_URI`, which caches the JWKS and refreshes it on an unknown `kid`. A `DelegatingOAuth2TokenValidator` additionally requires `exp`/`nbf` (via `JwtValidators.createDefaultWithIssuer`), `iss == JWT_ISSUER`, and `aud` containing `JWT_AUDIENCE`.
- **HS256 (legacy, no `kid`)** — only while `JWT_ACCEPT_LEGACY=true`; verified with the shared `JWT_SECRET` the same way as before, and still rejects tokens without an `exp` claim. TODO (marked in code): drop this branch once no client holds a pre-RS256 token.

Both paths resolve to a plain `Map<String, Object>` of claims (jjwt's `Claims` and Spring's `Jwt.getClaims()` are both maps), so `JwtAuthFilter`'s identity-extraction logic is shared between them.

### Error handling

`GlobalErrorHandler` (`exception/GlobalErrorHandler.java`, `@Order(-2)`) is the single place producing the uniform `{timestamp, status, error, message, path}` JSON error body for *uncaught* exceptions (connection failures → 503, timeouts → 504, everything else → 500 unless it's a `ResponseStatusException`). `JwtAuthFilter` has its own inline error writer for the 401 cases it owns, so it does not go through this handler.

### Observability

`management.endpoints.web.exposure` only exposes `health` and `info` via actuator; health details are hidden (`show-details: never`).
