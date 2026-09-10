# API Documentation

Lavanda Flow publishes its Spring MVC HTTP contract with springdoc-openapi.

## Development endpoints

When the backend is running with the local profile, the documentation endpoints are:

```text
OpenAPI JSON:  /v3/api-docs
OpenAPI YAML:  /v3/api-docs.yaml
Swagger UI:    /swagger-ui.html
```

The generated specification is the primary source for HTTP contract discovery. Add explicit OpenAPI annotations only when inferred metadata is incomplete or when a contract detail cannot be expressed clearly by the Java types and Spring MVC mapping.

## Security

Operational `/api/v1/**` routes use stateful Spring Security sessions. The session cookie is managed by the browser and server; it is not a bearer-token contract and Angular must not read or persist it. Public registration, password reset, JWT, OAuth, and refresh-token endpoints are not part of v0.6.0.

Authentication contracts:

```text
POST /api/v1/auth/login    public, CSRF-protected
GET  /api/v1/auth/session  public session/CSRF bootstrap
POST /api/v1/auth/logout   authenticated, CSRF-protected
```

Login accepts `username` and `password` JSON fields and returns `200` with the authenticated state and canonical username. Invalid credentials return the generic `AUTHENTICATION_FAILED` error without revealing whether the username exists. Session bootstrap returns `200` with `authenticated` and a nullable `username`; it also materializes fresh CSRF state without returning the token or session identifier in JSON. Logout invalidates the session and returns `204`.

Angular uses Spring Security's same-origin XSRF boundary:

```text
cookie: XSRF-TOKEN
header: X-XSRF-TOKEN
```

The XSRF cookie is browser-readable; the separate session cookie is `HttpOnly`. A fresh client calls `GET /api/v1/auth/session` before login or another unsafe request and again when fresh CSRF state is needed after authentication or logout.

The server-side session has a 12-hour idle timeout. Its cookie uses path `/`, `HttpOnly`, and `SameSite=Lax`; `Secure=false` is the trusted-LAN HTTP default and external configuration must set `Secure=true` for an HTTPS profile.

The default operational profile disables Swagger UI and OpenAPI endpoints and exposes only minimal Actuator health. The `local` development profile enables `/v3/api-docs`, `/v3/api-docs.yaml`, `/swagger-ui.html`, and the existing local diagnostics. API documentation must never contain credentials, secrets, database connection details, tokens, or sensitive runtime configuration.

## Versioning

The initial application API prefix is `/api/v1`. The OpenAPI document version identifies the public contract family and does not replace normal application release versioning.

## Exact decimal quantities

Inventory and production quantities use `BigDecimal` / PostgreSQL `NUMERIC(19,6)` and are represented in v0.5.0 HTTP JSON as plain decimal strings. For example:

```json
{ "quantity": "9999999999999.123456" }
```

The Angular client sends canonical quoted decimal values and consumes quoted decimal responses without JavaScript `number` conversion. The backend continues to accept representative legacy numeric request values where Jackson already supports them. Quantity responses intentionally changed from v0.4.0 JSON numbers to strings: the former representation could silently mutate values in browser clients. This is a corrective wire-contract change, not byte-compatible v0.4 response payload behavior.
