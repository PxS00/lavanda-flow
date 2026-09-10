# Issue #178 — Implement operator authentication and secure sessions

## Objective

Implement the smallest complete backend authentication/session slice required by the accepted v0.6.0 local-first architecture so one Céu de Lavanda operator can authenticate from the host notebook or a tablet on the same trusted LAN.

The implementation must protect operational `/api/v1/**` routes with Spring Security server-side sessions, keep credentials in PostgreSQL as password hashes only, preserve CSRF protection for browser writes, and provide deterministic auth contracts for the Angular follow-up in #179.

This issue owns backend security behavior only. It must not implement frontend login UI, runtime/container packaging, workstation/network configuration, cloud hosting, or business authorization rules.

## Source of truth

Apply, in order:

1. GitHub issue #178;
2. `AGENTS.md`;
3. `backend/AGENTS.md`;
4. ADR 0010: `docs/architecture/decisions/0010-adopt-local-first-operator-hosted-runtime.md`;
5. `docs/architecture/architecture.md`;
6. `docs/architecture/dependencies.md`;
7. `docs/development/api-documentation.md`;
8. existing `shared` error/config conventions;
9. this specification for the approved implementation details of #178.

Issue #178 remains authoritative for objective, scope, acceptance criteria, constraints, and out-of-scope behavior. This specification resolves the implementation details that ADR 0010 intentionally delegated to #178.

## Branch

```text
feature/178/implement-operator-authentication
```

The branch starts from the current `develop` branch and targets `develop` through the normal issue PR flow.

## Existing state to preserve

- Backend: Java 25, Spring Boot 4.1, Spring MVC, Spring Security, Spring Data JPA, PostgreSQL, Flyway, Testcontainers, Maven.
- Spring Security production and test starters are already present; add no authentication dependency.
- The Spring Modulith modules remain `catalog`, `inventory`, `production`, `suppliers`, and `shared`.
- Security is cross-cutting and belongs under `shared`; do not create a new `auth`, `users`, or identity module.
- Existing business controllers and `/api/v1` wire contracts must remain unchanged.
- `ApiErrorResponse` is the repository-wide JSON error envelope.
- PostgreSQL is the source of truth and Flyway owns schema evolution.
- `application-local.yml` currently enables Swagger/OpenAPI for development.
- The base application configuration currently exposes Actuator `health`, `info`, and `prometheus`; ADR 0010 requires the operational/default profile to minimize this exposure.
- No existing `SecurityFilterChain`, `UserDetailsService`, `PasswordEncoder`, or operator-account persistence implementation exists on `develop`.

## Architecture boundary

Implement security inside the existing `shared` module, preferably under:

```text
com.ceudelavanda.lavandaflow.shared.security
```

Subpackages may separate web/config/persistence responsibilities when useful, but do not create an additional Spring Modulith module or named interface.

Business modules must not import security persistence/configuration types. Existing controllers should become protected through the security filter chain rather than through controller-specific changes.

## Operator account persistence

Add the next Flyway migration:

```text
backend/src/main/resources/db/migration/V14__create_operator_account.sql
```

Create one minimal table for authentication credentials:

```text
operator_account
- id UUID primary key
- username VARCHAR(100) not null
- password_hash VARCHAR(255) not null
```

Requirements:

- no plaintext password column;
- no email, display-name, role matrix, refresh token, MFA data, password-reset token, or external identity metadata;
- no `active` flag in #178 because ADR 0010 does not require account activation/deactivation;
- usernames are treated case-insensitively for authentication and bootstrap identity;
- enforce case-insensitive username uniqueness in PostgreSQL without adding the `citext` extension, for example with a unique index on `lower(username)`;
- reject blank/whitespace-only usernames with a database constraint where practical;
- password hashes must have enough capacity for Spring Security delegating hash prefixes and future adaptive encoders;
- existing Flyway migrations remain immutable.

The application may use a JPA persistence entity internal to `shared.security`. Do not expose that entity in HTTP responses.

## Password hashing

Use the existing Spring Security stack only.

Provide a `PasswordEncoder` using Spring Security's delegating adaptive strategy, preferably:

```text
PasswordEncoderFactories.createDelegatingPasswordEncoder()
```

This keeps the stored hash self-describing (for example `{bcrypt}...`) and avoids hard-coding a non-upgradable encoder contract.

Rules:

- encode only at account creation/bootstrap;
- authenticate through Spring Security's password verification path;
- never log the submitted password or encoded hash;
- never include the hash in exceptions, HTTP DTOs, actuator output, or diagnostics;
- do not compare raw passwords manually.

## Username semantics

- trim leading/trailing whitespace before lookup/bootstrap;
- reject blank usernames;
- maximum stored username length: 100 characters;
- authentication lookup is case-insensitive;
- return the canonical stored username in authenticated session responses;
- do not introduce email-as-identity or username-change workflows.

## Initial operator provisioning

Initial operator creation is a maintainer-only bootstrap operation, not a public HTTP endpoint.

Implement an explicit opt-in bootstrap mechanism under `shared.security` using typed configuration where practical.

Approved semantics:

```text
lavanda.security.bootstrap.enabled=false
lavanda.security.bootstrap.username=<external value>
lavanda.security.bootstrap.password=<external value>
```

Spring environment-variable relaxed binding or equivalent external configuration may provide the real values. Do not commit real values.

Behavior when bootstrap is disabled:

- no account is created;
- normal startup performs no credential mutation.

Behavior when bootstrap is explicitly enabled:

1. validate that username/password were supplied externally;
2. normalize/validate the username;
3. look up the operator case-insensitively;
4. if absent, hash the password and create exactly one account;
5. if already present, leave the existing account and password hash unchanged;
6. report only a non-sensitive outcome such as created/already-present;
7. never print username/password/hash together in logs and never print plaintext password;
8. do not reset or overwrite an existing password merely because bootstrap variables remain configured.

The mechanism must be safe to rerun.

A maintainer should remove plaintext bootstrap password material from the runtime environment/configuration after successful provisioning where practical. #185/#186 will document the real workstation procedure.

Do not add public registration, a password-reset endpoint, an email flow, or automatic startup credentials.

## Authentication principal

Back authentication with the persisted operator account using Spring Security's standard username/password authentication machinery.

A repository-backed `UserDetailsService` or equivalent Spring Security adapter may expose only what authentication requires:

- canonical username;
- stored password hash;
- authenticated operator authority/principal state.

No role column is required. Authorization in #178 is based on `authenticated()` rather than a role hierarchy or permission matrix.

A fixed in-memory authority may be used internally if Spring Security APIs require one, but no public role contract and no persisted role model should be introduced.

## HTTP contract

All auth endpoints are under:

```text
/api/v1/auth
```

### `POST /api/v1/auth/login`

Public authentication endpoint, but still CSRF-protected.

Request JSON:

```json
{
  "username": "operator",
  "password": "secret"
}
```

Validation:

- `username`: required/nonblank, max 100;
- `password`: required/nonblank;
- do not echo credentials in errors.

Success:

```text
HTTP 200
```

```json
{
  "authenticated": true,
  "username": "operator"
}
```

The successful authentication must establish and persist a real server-side Spring Security session. A custom JSON login implementation must use Spring Security's authentication/session machinery correctly, including session-fixation protection and `SecurityContextRepository` persistence. Merely putting an `Authentication` into `SecurityContextHolder` without persisting it is not acceptable.

Prefer a Spring-native authentication-processing mechanism. If a controller performs the JSON boundary, it must explicitly preserve the configured `SessionAuthenticationStrategy` and save the `SecurityContext` through the configured repository.

Invalid credentials:

```text
HTTP 401
code: AUTHENTICATION_FAILED
message: Authentication failed
```

Unknown username and wrong password must produce the same externally observable status/code/message so the API does not reveal account existence.

### `GET /api/v1/auth/session`

Public session-bootstrap endpoint used by Angular before rendering protected routes and before first login.

Unauthenticated:

```text
HTTP 200
```

```json
{
  "authenticated": false,
  "username": null
}
```

Authenticated:

```text
HTTP 200
```

```json
{
  "authenticated": true,
  "username": "operator"
}
```

This endpoint must materialize the SPA CSRF token/cookie when necessary so a fresh browser can subsequently submit the CSRF-protected login request. Do not return the CSRF token or session identifier in the JSON body.

### `POST /api/v1/auth/logout`

Authenticated and CSRF-protected.

Success:

```text
HTTP 204
```

Logout must:

- invalidate the authenticated HTTP session;
- clear the Spring Security context;
- use Spring Security logout support rather than leaving stale authenticated state;
- rotate/clear CSRF state according to the framework SPA pattern;
- prevent the previous session from accessing protected routes.

After logout, `GET /api/v1/auth/session` must report unauthenticated state and materialize fresh CSRF state for a later login.

## Session policy

Use normal servlet/server-side sessions. Do not introduce Spring Session, Redis, JDBC session persistence, bearer tokens, JWT, remember-me cookies, or refresh tokens.

Configure the v0.6.0 session contract:

```text
idle timeout: 12 hours
session cookie path: /
HttpOnly: true
SameSite: Lax
Secure: false by default for the accepted trusted-LAN HTTP profile
```

`Secure` must be externally configurable so an HTTPS profile can enable it without changing auth/API contracts. Prefer an environment-backed property instead of branching Java behavior on guessed host details.

The session cookie itself remains browser-managed; Angular must never need to read it.

Application restart may invalidate active sessions. Persisting servlet sessions across application restarts is not required in #178.

## CSRF / Angular XSRF boundary

CSRF remains enabled for unsafe browser methods, including login and logout.

Use the Spring Security SPA CSRF integration supported by the project's resolved Spring Security version, following Angular's conventional boundary:

```text
cookie: XSRF-TOKEN
request header: X-XSRF-TOKEN
```

The XSRF token cookie must be browser-readable as required by Angular; this is separate from the `HttpOnly` session cookie.

Prefer Spring Security's native SPA CSRF configuration rather than custom token formats or a second security protocol.

Important lifecycle behavior:

- an initial `GET /api/v1/auth/session` produces/materializes XSRF state;
- login requires a valid CSRF token;
- successful authentication rotates/clears stale CSRF state according to Spring Security semantics;
- the client can call `GET /api/v1/auth/session` to obtain fresh XSRF state after login/logout when needed;
- invalid or missing CSRF on a state-changing request returns a deterministic safe 403 JSON error.

Do not disable CSRF for `/api/v1/auth/login`, `/api/v1/auth/logout`, or business writes.

## Authorization rules

The filter chain must express the route boundary centrally.

Public operational/bootstrap routes:

```text
POST /api/v1/auth/login
GET  /api/v1/auth/session
GET  /actuator/health
```

`POST /api/v1/auth/logout` requires authentication.

All other operational routes under:

```text
/api/v1/**
```

require authentication.

Do not edit every business controller to add authorization annotations.

Non-API SPA/static GET resources must remain capable of being served unauthenticated in the future #182 same-origin packaging so the login page itself can load. The global fallback should therefore not accidentally require authentication for all static resources.

Do not add business-role checks.

## Swagger/OpenAPI and Actuator exposure

Align configuration with ADR 0010.

Operational/default profile:

- keep `/actuator/health` exposed;
- health must reveal only minimal/non-sensitive information to unauthenticated clients;
- do not expose Prometheus/info unnecessarily;
- disable Swagger UI/OpenAPI endpoints by default for the operational profile.

Development/local profile:

- may continue enabling Swagger/OpenAPI;
- may continue exposing development diagnostics such as info/prometheus where already useful;
- these development capabilities must not weaken the default operational profile.

The security chain may permit documentation/diagnostic paths that are disabled by default and enabled only by the local development profile. Do not add a second security architecture solely for development.

## Security error contract

Security failures happen in filters before `GlobalExceptionHandler`, so implement the smallest shared writer/handlers needed to preserve the repository `ApiErrorResponse` envelope.

Required externally observable errors:

### Unauthenticated protected route

```text
HTTP 401
code: AUTHENTICATION_REQUIRED
message: Authentication is required
```

### Invalid login credentials

```text
HTTP 401
code: AUTHENTICATION_FAILED
message: Authentication failed
```

### Authenticated but forbidden access

```text
HTTP 403
code: ACCESS_DENIED
message: Access is denied
```

### Missing/invalid CSRF token

```text
HTTP 403
code: CSRF_VALIDATION_FAILED
message: CSRF token is missing or invalid
```

Use the existing `ApiErrorResponse` shape:

```text
timestamp
status
error
code
message
path
details
```

`details` may be null when no safe field-level detail is useful.

Use the application `Clock` for timestamps so security responses remain deterministic/testable.

Do not return framework exception messages, stack traces, submitted usernames/passwords, hashes, session IDs, CSRF tokens, database details, or internal class names.

## Configuration changes

Update base/default configuration only as needed for the accepted operational profile:

- 12-hour servlet session timeout;
- session cookie `HttpOnly`, `SameSite=Lax`, `/` path;
- externally configurable `Secure` flag with trusted-LAN HTTP default false;
- default operational springdoc disabled;
- default management exposure minimized to health.

Update `application-local.yml` as needed so existing development Swagger/OpenAPI and useful local diagnostics remain available.

Do not configure TLS, domains, router/firewall rules, Docker runtime, workstation auto-start, or public CORS in #178.

Operational CORS remains same-origin. #179 may use the Angular development proxy rather than requiring permissive backend CORS.

## API documentation

Update:

```text
docs/development/api-documentation.md
```

Document:

- stateful session authentication;
- login/session/logout contracts;
- session cookie is server-managed and not an Angular token contract;
- Angular XSRF cookie/header names;
- `GET /api/v1/auth/session` as both auth bootstrap and CSRF materialization boundary;
- default operational Swagger/OpenAPI restriction versus local development availability;
- no credentials/secrets in docs.

Do not add operator workstation instructions; those belong to #185/#186.

## Testing strategy

Use PostgreSQL/Testcontainers for persistence/integration behavior. Do not use H2.

Add focused coverage for at least:

### Persistence / password

- Flyway V14 applies on fresh PostgreSQL;
- case-insensitive username uniqueness;
- plaintext password is never persisted;
- stored hash differs from raw password;
- configured `PasswordEncoder` verifies the stored hash.

### Bootstrap

- disabled bootstrap creates nothing;
- enabled bootstrap with valid external values creates one account;
- rerunning bootstrap for the same username leaves the existing hash unchanged;
- case variation resolves to the same existing operator;
- missing/blank required bootstrap values fail deterministically without logging secrets.

### Authentication/session

- valid credentials return 200 and establish a persistent authenticated HTTP session;
- wrong password returns the generic 401 contract;
- unknown username returns the exact same generic 401 contract;
- authenticated `GET /api/v1/auth/session` returns username;
- unauthenticated session bootstrap returns 200/false/null;
- login changes/replaces any pre-authentication session identifier when one exists, preserving session-fixation protection;
- logout returns 204 and the previous session cannot access protected routes;
- 12-hour timeout/cookie configuration is bound as intended.

### Authorization

- a representative existing GET under `/api/v1/**` returns 401 without session and succeeds/reaches its normal application behavior with an authenticated session;
- a representative existing state-changing business endpoint is protected by both authentication and CSRF;
- business controllers themselves do not require auth-specific changes.

### CSRF

- session bootstrap materializes `XSRF-TOKEN`;
- unsafe request without CSRF returns deterministic 403 `CSRF_VALIDATION_FAILED`;
- unsafe request with valid Angular-compatible XSRF cookie/header succeeds past CSRF validation;
- login and logout are CSRF-protected;
- fresh token acquisition after authentication/logout works through session bootstrap.

### Cookie profile

Default trusted-LAN HTTP profile:

- session cookie is `HttpOnly`;
- session cookie is `SameSite=Lax`;
- session cookie path is `/`;
- session cookie is not incorrectly marked `Secure`.

HTTPS-configured test profile/property:

- the same session contract can set `Secure=true` without code/API changes.

### Exposure

- operational/default configuration exposes minimal health only;
- Swagger/OpenAPI are disabled in the operational/default profile;
- local development configuration can still enable documentation as intended;
- protected/denied management paths do not leak sensitive details.

### Module/architecture

- existing Spring Modulith verification remains green;
- no business module acquires a dependency on security internals;
- no new dependency is added.

## Validation

Run from `backend/`:

```bash
./mvnw verify
```

Also run from repository root:

```bash
git diff --check
git status --short
```

Review the complete diff and confirm there are no frontend, runtime/container, workstation, or unrelated business changes.

## Acceptance-criteria mapping

The implementation is complete only when:

- ADR 0010's stateful operator-session architecture is implemented without changing it;
- operator credentials are PostgreSQL-persisted through Flyway V14 with password hashes only;
- the approved adaptive Spring Security `PasswordEncoder` is used;
- no default plaintext credential exists in repository artifacts;
- bootstrap is explicit, deterministic, safe to rerun, and never overwrites an existing password;
- valid login establishes a server-side authenticated session;
- invalid username/password are indistinguishable externally;
- `GET /api/v1/auth/session` provides stable authenticated/unauthenticated JSON bootstrap state;
- logout invalidates authenticated access;
- all operational `/api/v1/**` endpoints except the explicit auth bootstrap routes require authentication;
- CSRF remains enabled for state-changing requests with Angular-compatible XSRF behavior;
- session cookie semantics match ADR 0010, including 12-hour idle timeout and HTTP-profile `Secure=false`;
- HTTPS can enable `Secure=true` by external configuration only;
- 401/403/CSRF errors use safe deterministic `ApiErrorResponse` JSON;
- default operational documentation/management exposure is minimized;
- local development Swagger/OpenAPI remains intentionally available;
- no JWT/OAuth/SSO/MFA/external identity/security dependency is added;
- integration tests cover the risks listed above;
- Spring Modulith verification remains green;
- `./mvnw verify` succeeds.

## Out of scope

- Angular login/session UI or route guards (#179);
- Docker/runtime packaging (#182);
- workstation, firewall, DHCP, hostname, auto-start, or OS integration (#186);
- backup implementation (#184);
- public internet access, TLS, DNS, domain, VPN, or router port forwarding;
- JWT, bearer tokens, refresh tokens, OAuth, SSO, social login, external IdP, MFA;
- public registration;
- email password reset;
- complex RBAC or persisted permissions;
- remember-me authentication;
- persistent servlet sessions across application restarts;
- login rate-limiting infrastructure;
- account-management UI;
- password-change HTTP endpoint;
- operator activation/deactivation state;
- inventory, production, supplier, or catalog behavior changes;
- minimum-shelf-life policy #73.

## Final implementation report

The Codex implementation report must include:

1. files created/modified;
2. security/session design implemented;
3. persistence/bootstrap decisions;
4. tests added;
5. any production code/configuration changed and why;
6. exact `./mvnw verify` result and test count;
7. `git diff --check` result;
8. any acceptance criterion still unsatisfied;
9. `git status --short`.
