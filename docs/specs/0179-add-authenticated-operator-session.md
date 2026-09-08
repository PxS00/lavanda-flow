# Issue #179 — Add authenticated operator session

## Objective

Implement the smallest complete Angular authentication experience required for the Céu de Lavanda operator to use Lavanda Flow from the operator notebook or a tablet on the same trusted LAN.

The frontend must consume the stateful Spring Security session contract delivered by #178, restore authenticated state from the backend on reload, protect operational routes, provide a pt-BR login/logout experience, and keep credentials/session identifiers out of frontend persistence.

This issue owns frontend authentication/session integration only. It must not change backend security contracts, inventory/production business behavior, runtime packaging, workstation integration, remote access, or public hosting.

## Source of truth

Apply, in order:

1. GitHub issue #179;
2. `AGENTS.md`;
3. `frontend/AGENTS.md`;
4. ADR 0010: `docs/architecture/decisions/0010-adopt-local-first-operator-hosted-runtime.md`;
5. backend auth/session contract delivered by #178 and documented in `docs/development/api-documentation.md`;
6. existing Angular routing, core HTTP, layout, localization, and testing conventions;
7. this specification for the approved implementation details of #179.

Issue #179 remains authoritative for objective, scope, acceptance criteria, constraints, and out-of-scope behavior.

## Branch

```text
feature/179/add-authenticated-operator-session
```

The branch starts from current `develop` after #178 and targets `develop` through the normal issue PR flow.

## Existing state to preserve

- Angular 22 standalone application.
- Angular Router with a top-level `ApplicationShell` and lazy feature routes.
- Native Angular `HttpClient`.
- Angular Material/CDK.
- Reactive Forms.
- Signals for local/application state; RxJS for HTTP composition where justified.
- Vitest through the Angular unit-test builder.
- Production `environment.apiBaseUrl` is already relative: `/api/v1`.
- Development `environment.apiBaseUrl` currently uses `http://localhost:8080/api/v1`; this conflicts with the accepted same-origin cookie/XSRF browser model and must be corrected in this issue.
- Existing `mapHttpError`, `localizeUiError`, `ApiErrorResponse`, and pt-BR error-localization conventions remain the shared error boundary.
- Existing business feature API services and wire values must remain unchanged.
- Existing application shell remains the protected operator shell.

## Delivered backend contract from #178

### Session bootstrap

```http
GET /api/v1/auth/session
```

Unauthenticated response:

```json
{
  "authenticated": false,
  "username": null
}
```

Authenticated response:

```json
{
  "authenticated": true,
  "username": "Operator"
}
```

This endpoint is public and materializes the browser-readable CSRF cookie required by the SPA.

### Login

```http
POST /api/v1/auth/login
```

Request:

```json
{
  "username": "operator",
  "password": "secret"
}
```

Success:

```json
{
  "authenticated": true,
  "username": "Operator"
}
```

Invalid credentials return `401` with code `AUTHENTICATION_FAILED`.

Login is CSRF-protected.

### Logout

```http
POST /api/v1/auth/logout
```

Authenticated and CSRF-protected. Success returns `204` and invalidates the server-side session.

### XSRF contract

```text
cookie: XSRF-TOKEN
header: X-XSRF-TOKEN
```

The separate session cookie remains `HttpOnly`; Angular must not attempt to inspect or persist it.

## Frontend architecture boundary

Authentication/session infrastructure belongs under `core`, for example:

```text
frontend/src/app/core/auth/
├── auth-api.service.ts
├── auth.dto.ts
├── auth-session.service.ts
├── auth.guard.ts
└── auth-session.interceptor.ts
```

The operator login page belongs to a focused frontend feature, for example:

```text
frontend/src/app/features/auth/pages/login-page/
```

Exact filenames may vary slightly when existing repository conventions justify it, but preserve the responsibility split:

- `core/auth` owns HTTP session integration, state, route protection, and 401 handling;
- the login page owns presentation/form interaction only;
- `ApplicationShell` owns the visible logout action but not authentication rules;
- inventory, production, catalog, suppliers, receipts, and dashboard components do not implement auth independently.

Do not introduce NgRx, an external auth SDK, Axios, token refresh infrastructure, or a new dependency.

## Auth DTOs and API client

Provide typed contracts for the backend payloads.

Suggested shapes:

```ts
export interface AuthSessionDto {
  readonly authenticated: boolean;
  readonly username: string | null;
}

export interface LoginRequest {
  readonly username: string;
  readonly password: string;
}
```

The auth API client must use the existing `API_BASE_URL` token and relative `/api/v1` contract, matching existing typed API-service conventions.

It must provide only the required operations:

- `getSession()`;
- `login(request)`;
- `logout()`.

Do not add bearer headers, token DTOs, refresh endpoints, role DTOs, registration, or password-reset APIs.

## Same-origin development requirement

The browser contract is same-origin in both operational and local-development usage.

Change development API routing from:

```text
http://localhost:8080/api/v1
```

to the same relative boundary used by production:

```text
/api/v1
```

Add an Angular development proxy that forwards `/api` to the local Spring Boot process at `http://localhost:8080`.

The proxy is development tooling only and must not change the v0.6.0 operational topology selected by ADR 0010.

Requirements:

- browser requests remain same-origin from the Angular dev server's perspective;
- session and XSRF cookies are handled by the browser normally;
- no permissive backend CORS configuration is introduced;
- do not hard-code a LAN/public hostname in frontend feature code;
- production remains relative `/api/v1`.

## Angular XSRF integration

Use Angular `HttpClient`'s built-in XSRF support.

Configure the accepted names explicitly at the application provider boundary:

```text
cookieName: XSRF-TOKEN
headerName: X-XSRF-TOKEN
```

Do not manually read `document.cookie` to construct XSRF headers.

Do not expose or manipulate the session cookie.

Do not add `withCredentials` as a workaround for the previous cross-origin development URL; the dev proxy removes that cross-origin boundary.

## Session state model

Provide one root-scoped auth-session service/store as the frontend source of truth for browser authentication state.

A minimal state model should distinguish at least:

```text
unknown/loading
unauthenticated
authenticated
```

A temporary connectivity/error state may be represented when needed to avoid treating an unreachable backend as a valid authenticated state.

Authenticated state contains only safe frontend information required by the UI, currently the canonical username.

The state must never contain:

- password;
- `JSESSIONID` or another session identifier;
- XSRF token value;
- JWT/access/refresh tokens;
- password hashes.

Do not persist auth state in:

- `localStorage`;
- `sessionStorage`;
- IndexedDB;
- URL query/fragment values;
- logs.

Backend `/auth/session` is authoritative after load/reload.

## Session bootstrap and reload

Every fresh application entry must establish session state from:

```http
GET /api/v1/auth/session
```

Do not assume a previous in-memory signal remains valid after browser reload.

The implementation may make bootstrap idempotent/cached while a request is in flight, but it must support an explicit refresh after login/logout because the backend rotates/clears CSRF state at those boundaries.

Required behavior:

1. Direct navigation to a protected route with a valid backend session:
   - bootstrap session;
   - retain the requested protected route;
   - render the protected shell/page.

2. Direct navigation to a protected route without a valid backend session:
   - bootstrap session;
   - redirect to `/login` before protected content renders.

3. Direct navigation to `/login` while a valid backend session already exists:
   - bootstrap session;
   - navigate to `/dashboard` rather than presenting a redundant login form.

4. Backend/network bootstrap failure:
   - do not present stale protected data as authenticated;
   - show a safe pt-BR connectivity/server message and allow retry.

Do not introduce an application-wide initializer that permanently prevents Angular from rendering when the backend is unavailable. Route/page bootstrap may coordinate session loading while allowing a usable error/login surface.

## Route protection

Add a public `/login` route outside `ApplicationShell`.

Protect the application-shell route and its children through Angular Router guards.

The guard must:

- use the centralized auth-session service;
- bootstrap from the backend when state is unknown;
- allow access when backend-confirmed session state is authenticated;
- return a router `UrlTree` redirect to `/login` when unauthenticated;
- avoid imperative navigation from the guard where a `UrlTree` is sufficient.

Do not add auth logic independently to feature route files/components.

A single protected shell boundary plus centralized expired-session handling is preferred over duplicating guards on every individual feature.

## Login page

Create a focused pt-BR login page with Reactive Forms.

Required fields:

- username;
- password.

Use semantic labels and browser autocomplete values suitable for existing credentials:

```text
username
current-password
```

Password input must use `type="password"`.

Minimum validation:

- username required;
- username maximum 100 characters to match the backend contract;
- password required.

User-visible copy and accessibility names are pt-BR.

Suggested operator wording may include:

- title: `Entrar no Lavanda Flow`;
- username label: `Usuário`;
- password label: `Senha`;
- submit action: `Entrar`.

Invalid backend credentials must map to a safe generic pt-BR message such as:

```text
Usuário ou senha incorretos.
```

Do not reveal whether the username exists.

Disable duplicate submissions while login is in progress.

A login failure must not place the password in durable component/application state beyond the reactive form control required for the immediate request. Clear the password field after a failed or completed login when practical.

## Login CSRF lifecycle

A fresh browser requires `GET /api/v1/auth/session` before the first login so the backend can materialize `XSRF-TOKEN`.

The login UI must not enable a submit path that bypasses the required bootstrap when the frontend does not yet have usable session/CSRF state.

After a successful login, the backend rotates session/CSRF state. Before considering the authenticated flow fully ready for later unsafe requests, refresh through:

```http
GET /api/v1/auth/session
```

This post-login bootstrap must:

- confirm canonical authenticated state;
- materialize fresh post-authentication XSRF state;
- prevent the first later inventory/production write from using stale pre-login XSRF state.

Do not rely only on the `POST /auth/login` response for the final ready state.

## Logout flow

Add a visible `Sair` action to `ApplicationShell`.

On logout:

1. call `POST /api/v1/auth/logout`;
2. on success, clear authenticated frontend state;
3. call public `GET /api/v1/auth/session` to materialize fresh unauthenticated XSRF state for a later login;
4. navigate to `/login`.

The operator username may be shown in the shell if this remains simple and responsive.

Do not claim logout succeeded when the backend request failed due to a connectivity/server error; the server-side session may still exist. Keep the operator informed with safe pt-BR feedback and avoid stale optimistic security state.

If logout returns `401` because the session has already expired, treat the frontend as unauthenticated and transition to `/login`; then bootstrap fresh unauthenticated CSRF state.

## Expired session / global 401 handling

Add one functional `HttpInterceptorFn` for authenticated-session expiration.

For `401` responses from normal operational APIs:

- mark centralized auth state unauthenticated;
- stop treating currently displayed protected data as authoritative;
- navigate to `/login`;
- allow the original HTTP error to propagate so callers do not falsely treat the request as successful.

Avoid redirect loops or destructive handling for expected auth-endpoint responses:

- invalid login `401` must remain a login-form error;
- `/auth/session` is expected to return `200` unauthenticated by contract;
- logout expiration may transition to unauthenticated state.

Do not automatically retry state-changing business requests after authentication or CSRF failures.

## Security error localization

Extend existing centralized frontend localization only where required for the new backend codes:

```text
AUTHENTICATION_FAILED
AUTHENTICATION_REQUIRED
ACCESS_DENIED
CSRF_VALIDATION_FAILED
```

Operator-visible messages remain pt-BR.

Do not display raw backend/framework messages when a known safe localized message exists.

CSRF failures should provide actionable but non-sensitive feedback. Do not automatically replay the failed write.

## Application shell integration

Keep `ApplicationShell` focused on layout and operator actions.

Allowed additions:

- authenticated username display if useful;
- `Sair` button;
- logout loading/error feedback.

Do not move auth orchestration rules into the shell; call the centralized auth-session service.

The shell remains responsive for notebook and tablet usage.

## Tablet and accessibility requirements

The login and logout experience must support:

- keyboard-only operation;
- visible labels;
- appropriate form validation association;
- `role="alert"` or equivalent accessible announcement for authentication errors;
- usable layout on handset/tablet widths;
- no hover-only interaction;
- sufficiently large Material controls for touch usage.

Do not introduce a desktop-only fixed-width layout that breaks the current mobile-first shell direction.

## Existing business behavior to preserve

Authentication integration must not:

- calculate or duplicate FEFO/expiration rules;
- change inventory write semantics;
- change production allocation/genealogy behavior;
- change exact-decimal string contracts;
- add optimistic stock updates;
- alter catalog/supplier/receipt/production API routes or DTO wire values;
- change backend security contracts delivered by #178.

The issue protects existing capabilities; it does not modify them.

## Tests

Add focused tests that prove behavior rather than implementation details.

### Auth API client

Cover:

- `GET /auth/session` URL and typed response;
- login request body and URL;
- logout URL;
- relative `/api/v1` behavior.

### Auth session service

Cover:

- unauthenticated bootstrap;
- authenticated bootstrap with canonical username;
- login success followed by session refresh;
- login failure without credential persistence;
- logout success clears state and refreshes unauthenticated CSRF/session bootstrap;
- bootstrap/network failure does not leave stale authenticated state.

### Router guard

Cover:

- authenticated access;
- unknown state triggers backend bootstrap;
- unauthenticated redirect to `/login`;
- valid session on reload retains protected route.

### 401 interceptor

Cover:

- operational API `401` transitions session state to unauthenticated and redirects to `/login`;
- login `401` remains available to the login page as an invalid-credentials error;
- original HTTP error still propagates;
- no automatic business-request retry.

### Login page

Cover:

- pt-BR labels/accessibility names;
- required validation;
- username max length;
- submit disabled while session bootstrap/login is pending;
- successful login navigates into the app only after post-login session refresh;
- invalid credentials show generic pt-BR feedback;
- password field is not rendered/logged outside the password input/request and is cleared after failure/completion where implemented;
- existing valid session redirects away from login.

### Application shell

Cover:

- logout action exists;
- successful logout navigates to `/login`;
- failed network logout does not falsely claim a server-side logout;
- responsive navigation behavior remains intact.

### XSRF and development proxy

Cover through configuration-focused tests where practical:

- Angular XSRF cookie/header names match `XSRF-TOKEN` / `X-XSRF-TOKEN`;
- development API base remains `/api/v1`;
- development proxy forwards `/api` to local backend;
- no auth token/storage implementation is introduced.

### Existing suite impact

Update existing route/application workflow tests only as required by the new protected shell boundary. Do not weaken or remove business assertions merely to accommodate authentication.

## Validation

From `frontend/` run:

```bash
pnpm lint
pnpm test
pnpm build
```

Also run from the repository root:

```bash
git diff --check
git diff
git status --short
```

Do not use `pnpm test -- --run`.

Known existing non-blocking build warnings should remain recorded rather than silently changing unrelated budgets.

## Acceptance criteria mapping

The implementation is complete only when all issue #179 criteria are demonstrably satisfied, including:

- backend #178 contract consumed without reinterpretation;
- unauthenticated operator cannot render protected shell routes;
- valid login establishes/restores backend-confirmed session state;
- invalid credentials are safe and localized;
- reload restores through backend session bootstrap;
- logout invalidates through backend contract and frontend state follows it;
- operational `401` expires frontend session state promptly;
- Angular XSRF integration works on login/logout/business writes;
- no credentials/session tokens are persisted in browser storage;
- production and development both use relative `/api/v1` from browser perspective;
- notebook/tablet accessibility is preserved;
- tests/lint/build pass.

## Out of scope

Do not implement:

- backend security changes;
- JWT/bearer/refresh tokens;
- OAuth, SSO, social login, MFA;
- public registration;
- password reset/change flows;
- roles/permission-based navigation;
- PWA/offline auth;
- public internet exposure;
- TLS/domain/DNS/VPN configuration;
- Docker/runtime packaging;
- workstation startup integration;
- inventory/production feature changes;
- speculative persistent auth stores.

## Expected implementation footprint

A complete solution is expected to remain concentrated in:

```text
frontend/src/app/core/auth/**
frontend/src/app/features/auth/**
frontend/src/app/app.config.ts
frontend/src/app/app.routes.ts
frontend/src/app/core/layout/application-shell/**
frontend/src/app/core/http/localize-ui-error.*
frontend/src/environments/environment.development.ts
frontend/angular.json
frontend/proxy.conf.json
relevant frontend tests
```

Do not modify backend production code or dependencies for this issue.

## Final implementation report

Before commit/push, report:

1. files created/modified;
2. auth/session architecture implemented;
3. route/session/XSRF lifecycle decisions;
4. login/logout UX implemented;
5. tests added/updated;
6. production code/configuration changed and why;
7. `pnpm lint` result;
8. `pnpm test` result and test count;
9. `pnpm build` result and warnings;
10. `git diff --check` result;
11. acceptance criteria still unsatisfied, if any;
12. `git status --short`.

Do not commit, push, or open a PR during implementation.