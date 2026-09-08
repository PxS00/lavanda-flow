# Issue #182 — Add local operational runtime

## Objective

Implement the repository-owned local operational runtime approved by ADR 0010 so Lavanda Flow can run on the operator notebook as a two-service Docker Compose deployment with no recurring infrastructure cost and no development tooling required for daily use.

The runtime must package the Angular production application into the Spring Boot application, run PostgreSQL with persistent private storage, expose only the application HTTP entry point to the trusted LAN, and keep developer-local behavior separate from operator runtime behavior.

## Source of truth

Apply, in order:

1. GitHub issue #182;
2. `AGENTS.md`;
3. `backend/AGENTS.md`;
4. `frontend/AGENTS.md`;
5. ADR 0010: `docs/architecture/decisions/0010-adopt-local-first-operator-hosted-runtime.md`;
6. `docs/specs/0177-define-local-first-readiness-baseline.md`;
7. `docs/specs/0178-implement-operator-authentication.md`;
8. `docs/specs/0179-add-authenticated-operator-session.md`;
9. current root `compose.yaml`, backend configuration, frontend production configuration, build files, and operational documentation;
10. this specification for the approved implementation details of #182.

Issue #182 remains authoritative for objective, scope, acceptance criteria, constraints, and out-of-scope behavior.

## Branch

```text
chore/182/add-local-operational-runtime
```

The branch is based on `develop` after #181 was squash-merged.

## Dependency state

#177 is complete and ADR 0010 is accepted. #182 is therefore no longer blocked.

The repository-owned runtime contract is already decided:

```text
Docker Compose
├── lavanda-flow-app
└── postgres
```

The actual notebook operating system, firewall, stable LAN address, Docker engine auto-start, shortcuts, and other host-specific integration remain owned by #186. Do not invent an operating system in #182.

## Existing state to preserve

The current repository already has:

- root `compose.yaml` used for developer-local PostgreSQL convenience;
- `application-local.yml` enabling Spring Boot Docker Compose lifecycle, Swagger/OpenAPI, and development diagnostics;
- default `application.yml` with the v0.6.0 operational security/session baseline and minimal `/actuator/health` exposure;
- Angular production configuration using relative `apiBaseUrl: '/api/v1'`;
- Spring Security stateful sessions and Angular same-origin XSRF integration from #178/#179;
- PostgreSQL 17 and Flyway-owned schema evolution;
- Java 25 backend and Angular 22 frontend;
- Maven wrapper, pnpm lockfile/package-manager declaration, and normal backend/frontend verification commands.

The existing root `compose.yaml` is a developer convenience file. It currently publishes PostgreSQL and provides development defaults. Do not silently convert it into the operator runtime if doing so would break developer-local behavior.

## Approved repository topology

Create a distinct operational runtime boundary while preserving the developer compose workflow.

Preferred repository shape:

```text
Dockerfile
.dockerignore
compose.operational.yaml
operational.env.example
backend/
frontend/
docs/operations/local-operational-runtime.md
```

Equivalent filenames are acceptable only when they make the operational/development distinction at least as explicit.

Do not add a third long-running service.

### Operational Compose project isolation

The operational Compose configuration must use a stable project identity distinct from the developer compose project so operational PostgreSQL storage cannot accidentally reuse the developer volume.

Prefer an explicit Compose project name such as:

```text
lavanda-flow-operational
```

or an equally deterministic isolation mechanism.

The operational PostgreSQL volume must be distinct from the development volume and must remain stable across application rebuilds/upgrades.

Do not use `container_name` unless a concrete implementation requirement justifies it.

## Application image

Use one application image for the Angular production bundle plus Spring Boot backend.

The image build must be multi-stage or equivalently isolated so development/build tooling does not become part of the final runtime image.

Required build behavior:

1. build Angular from the repository production configuration;
2. install frontend dependencies from the lockfile using the repository-declared pnpm version/contract;
3. use the production Angular build, not `ng serve` or another development server;
4. place the resulting browser assets into Spring Boot static resources before packaging the executable backend artifact;
5. package the backend with the Maven wrapper and Java 25-compatible build environment;
6. produce a final runtime image containing the packaged application and a Java 25-compatible runtime only;
7. do not require Node.js, pnpm, Maven, Angular CLI, an IDE, or source checkout inside the running application container.

Use the actual Angular output layout produced by the current builder. Do not hard-code an obsolete pre-Angular-application-builder output path without verifying it.

Use `pnpm install --frozen-lockfile` or the repository-equivalent locked install behavior during image build.

The image must not bake runtime secrets into layers through `ARG`, copied `.env` files, source files, or generated configuration.

Do not add a container registry requirement. The maintainer builds the image locally from an exact release/tag revision.

## Spring Boot serves the SPA

The Spring Boot application must serve:

- Angular production static assets;
- the Angular SPA entry document;
- existing `/api/v1/**` routes;
- existing minimal `/actuator/health`.

All browser application traffic therefore uses one local origin.

No Nginx, Caddy, Apache, frontend dev server, or reverse-proxy container is allowed solely for this purpose.

### SPA deep-link behavior

Direct browser navigation to valid Angular client routes must work after packaging, not only navigation from `/`.

If the backend currently lacks SPA fallback behavior, implement the smallest Spring MVC/static-resource forwarding needed so non-file browser GET routes can resolve to the Angular `index.html`.

At minimum validate representative routes such as:

```text
/
/login
/dashboard
```

The SPA fallback must not swallow or rewrite:

```text
/api/**
/actuator/**
/swagger-ui/**
/v3/api-docs/**
```

and must not interfere with real static files/assets.

Do not change Angular routing to hash-based routing merely to avoid implementing correct same-origin static serving.

Static SPA resources and the login application shell must remain loadable without an authenticated session; existing `/api/v1/**` security rules remain unchanged.

If a concrete blocker prevents Spring Boot from serving the Angular bundle correctly, stop and report it instead of adding a third service or changing ADR 0010 silently.

## Operational Spring profile

Use an explicit operational profile if needed to make runtime behavior deterministic. A suitable profile name is:

```text
operational
```

The operational runtime must not activate `local`.

The operational configuration must explicitly prevent Spring Boot development Docker Compose lifecycle behavior, for example by disabling `spring.docker.compose` in the operational profile.

Preserve the accepted operational defaults from #178:

- Swagger/OpenAPI disabled by default;
- management exposure limited to health;
- health details not publicly exposed;
- 12-hour stateful session timeout;
- HTTP-profile session cookie remains `HttpOnly`, `SameSite=Lax`, path `/`, `Secure=false` unless externally overridden for a future HTTPS profile;
- bootstrap disabled by default;
- no permissive operational CORS.

For operational persistence, Hibernate must not create/update schema. Keep Flyway authoritative and use `ddl-auto=validate` or an equally explicit non-mutating setting appropriate to the current project conventions.

Do not change application business behavior through this profile.

## Operational Docker Compose

The operational Compose file must contain exactly two long-running services:

```text
lavanda-flow-app
postgres
```

### `postgres`

Requirements:

- use the currently approved PostgreSQL major version;
- store `/var/lib/postgresql/data` in a named persistent operational volume;
- do not publish port `5432` to the host;
- remain reachable by `lavanda-flow-app` through the Compose-private network only;
- include a PostgreSQL readiness healthcheck using `pg_isready` or the existing equivalent;
- use `restart: unless-stopped` unless implementation proves a concrete incompatibility;
- obtain database name, username, and password from external runtime configuration;
- never contain a real/default operational password in tracked Compose configuration.

### `lavanda-flow-app`

Requirements:

- build from the repository application image path;
- depend on PostgreSQL readiness before normal startup;
- use `restart: unless-stopped` unless implementation proves a concrete incompatibility;
- activate only the operational runtime profile(s), never the developer `local` profile;
- configure the datasource to use the Compose service name `postgres`, not `localhost`;
- publish only the Spring Boot application HTTP port to the host/LAN;
- expose a configurable non-privileged host port with `8080` as the default unless the existing application requires a different value;
- do not publish a second backend/frontend port;
- do not mount source code or development directories into the running container;
- do not depend on browser lifecycle.

Use the existing `/actuator/health` endpoint as the lightweight maintainer health/status contract. Do not invent another health API.

An application-container Docker healthcheck is optional if it can be implemented without adding unnecessary runtime packages solely to perform HTTP probing. #183 will own automated runtime health validation.

## Runtime configuration and secrets

Provide a tracked example/template containing variable names and non-sensitive placeholders only.

A suitable tracked template is:

```text
operational.env.example
```

The real workstation file should be something ignored by the existing `.env*` rules, for example:

```text
.env.operational
```

The template may document safe defaults such as the local HTTP port, but it must not include a usable operational database password or operator password.

### Required database configuration

The operational Compose path must fail before or during startup with a clear configuration error when required database configuration is absent.

Prefer Compose required-variable interpolation for mandatory values, for example semantics equivalent to:

```text
POSTGRES_DB required
POSTGRES_USER required
POSTGRES_PASSWORD required
```

The application datasource must use those external values consistently.

### Optional runtime configuration

Preserve existing externally configurable values where useful, including:

```text
LAVANDA_SESSION_COOKIE_SECURE
EXPIRATION_ALERT_WINDOW_DAYS
```

Do not introduce a session secret unless the current application actually requires one.

### Operator bootstrap configuration

The runtime must support #178's existing external bootstrap properties without embedding credentials:

```text
lavanda.security.bootstrap.enabled
lavanda.security.bootstrap.username
lavanda.security.bootstrap.password
```

Use Spring's normal environment relaxed binding or current project mechanism.

Normal daily runtime starts with bootstrap disabled.

The tracked environment template may show bootstrap variable names with disabled/placeholder values only. It must clearly state that plaintext bootstrap password material should be removed from the workstation configuration after successful provisioning where practical.

Do not implement a new bootstrap mechanism, registration UI, password-reset service, or identity system.

The full operator provisioning procedure belongs to #185/#186; #182 only makes the runtime compatible with the already-implemented bootstrap mechanism.

## Persistence and destructive commands

Operational PostgreSQL data must survive:

- browser close;
- application-container restart;
- PostgreSQL-container restart;
- Docker/runtime restart;
- normal Compose `down` followed by `up`;
- application image rebuild/replacement;
- notebook reboot, assuming Docker/runtime starts again.

The technical runtime documentation must explicitly distinguish normal stop/restart from destructive volume removal.

Document that commands equivalent to:

```text
docker compose ... down -v
```

remove the database volume and are destructive. Do not present them as a normal operator/runtime command.

Backup/restore implementation remains #184 and must not be implemented here.

## Host startup boundary

Repository configuration in #182 provides only the portable runtime contract:

- both services use restart policy appropriate for runtime recovery;
- they remain alive independently of browser sessions;
- the Compose project can be started once by the maintainer and recovered automatically when the Docker engine/runtime restarts.

Do not add Linux `systemd`, Windows Task Scheduler, macOS launchd, Docker Desktop startup instructions, firewall rules, DHCP reservations, mDNS, shortcuts, or notebook power-policy changes in #182.

#186 will select and test the actual notebook OS integration.

## Developer-local compatibility

Preserve the existing developer workflow.

The current root `compose.yaml` and `application-local.yml` may continue to:

- publish PostgreSQL to the developer host;
- use developer defaults;
- use Spring Boot development Docker Compose lifecycle management;
- enable Swagger/OpenAPI and local diagnostics.

Do not make operator-runtime security/persistence constraints accidentally break development convenience.

If a compatibility change to the developer compose file is genuinely required, keep it minimal and document why.

Do not make the operational application depend on developer compose defaults.

## Technical operational documentation

Add or update a maintainer-facing technical reference under `docs/operations/`, preferably:

```text
docs/operations/local-operational-runtime.md
```

This is not the final go-live/operator runbook from #185.

Document only the repository runtime contract needed by later issues, including:

- prerequisites at the repository boundary: Docker with Compose support;
- exact-release checkout/build concept;
- copying the tracked environment template to the ignored workstation environment file;
- required environment variables without real values;
- build command;
- start command;
- status/health command or URL;
- restart command;
- normal stop command;
- statement that browser close does not stop services;
- statement that PostgreSQL is not host-published;
- persistence/volume identity;
- warning that volume deletion is destructive;
- distinction between developer `compose.yaml` and operational Compose;
- clear handoff to #184 for backup/restore and #186 for real workstation startup/LAN configuration.

Do not include real passwords, real private IP addresses, router credentials, or machine-specific paths.

## Tests

Add focused automated tests only where application code/config behavior needs protection. Do not create the #183 CI workflow in this issue.

### SPA/static serving

If #182 adds backend SPA forwarding/static-serving code, cover at least:

- `/` serves or resolves to the packaged SPA entry behavior;
- representative client route such as `/login` or `/dashboard` resolves to the SPA;
- `/api/v1/**` is not forwarded to `index.html`;
- `/actuator/**` is not forwarded to `index.html`;
- static assets/resources remain normal resources;
- the login/static shell remains reachable without authenticated business-session state.

Do not weaken Spring Security business-route protection to make these tests pass.

### Operational configuration

Where practical, add configuration tests proving:

- operational profile does not activate Spring Docker Compose lifecycle;
- Hibernate schema behavior remains non-mutating/validated;
- accepted session/security defaults remain unchanged.

Do not duplicate framework tests when declarative configuration is already sufficiently verified by runtime smoke validation.

## Local runtime smoke validation

If Docker is available to the implementing agent, validate the actual operational Compose path after normal unit/build verification.

Use temporary non-production credentials only.

At minimum verify:

1. operational Compose config resolves successfully with all required variables;
2. omission of a mandatory secret/config value fails clearly;
3. image builds from a clean repository working tree/context;
4. exactly two long-running services are created;
5. PostgreSQL has no published host port;
6. application HTTP is reachable on the configured host port;
7. `/actuator/health` reports healthy status without sensitive details;
8. `/` loads the Angular production application;
9. direct navigation to a representative Angular route works;
10. `/api/v1/auth/session` remains a real JSON API route and is not swallowed by SPA fallback;
11. Flyway migrations apply successfully to a fresh operational PostgreSQL volume;
12. restarting/recreating the application service against the same PostgreSQL volume succeeds;
13. normal Compose stop/start retains and reuses the operational database volume.

Do not use `down -v` against any non-disposable environment.

If Docker is unavailable, report the unperformed runtime smoke checks explicitly. Do not fake them or weaken acceptance criteria.

#183 will automate repository CI validation of the runtime and should consume the exact operational Compose/build path created here rather than inventing another one.

## Repository validation

Run all project validation because this issue crosses repository, backend packaging, and frontend production build boundaries.

From `backend/`:

```text
./mvnw verify
```

From `frontend/`:

```text
pnpm lint
pnpm test
pnpm build
```

From repository root:

```text
docker compose -f compose.operational.yaml --env-file <temporary-env> config

git diff --check
git diff
git status --short
```

Adapt the Compose filename if the approved equivalent name differs.

Known unrelated warnings should be reported rather than fixed opportunistically.

## Acceptance criteria mapping

### Runtime follows #177

Satisfied by the ADR 0010 two-service Docker Compose topology, same-origin application image, private PostgreSQL, persistent operational volume, and restart policy.

### No daily development tooling

Satisfied by the built application image. Build tooling exists only in image build stages; the running application needs Docker runtime/Compose only.

### Relative `/api/v1`

Satisfied by preserving the current Angular production environment contract and serving Angular + Spring Boot from one origin.

### Trusted-LAN notebook/tablet origin

Satisfied by publishing one configurable application port from the notebook host while keeping PostgreSQL private. Stable host addressing/firewall integration is #186.

### Persistence

Satisfied by the dedicated operational named volume and documented non-destructive lifecycle.

### No PostgreSQL LAN exposure

Satisfied only when operational Compose contains no host `ports` mapping for the PostgreSQL service.

### No tracked secrets

Satisfied by required external configuration plus placeholder-only tracked template and no secrets in image build args/layers.

### Missing configuration failure

Satisfied by explicit required-value validation/interpolation rather than falling back to operational credentials.

### No development Compose lifecycle

Satisfied by an operational profile/configuration that explicitly disables Spring Boot Docker Compose lifecycle and never activates `local`.

### Browser-independent lifecycle

Satisfied by Docker services/restart policies; browser state is not process ownership.

### Automatic host-start compatibility

Satisfied at repository boundary by restart policies and stable Compose project. Actual Docker engine host startup is implemented/tested in #186.

### Fresh Flyway migration and restart

Satisfied by real operational runtime smoke validation against fresh and reused PostgreSQL volumes; automated CI coverage follows in #183.

### Zero recurring infrastructure

Satisfied by local image build + local Docker Compose with no cloud, domain, registry, or paid service.

### Developer workflow preserved

Satisfied by keeping developer `compose.yaml`/`application-local.yml` semantics separate from operational runtime.

## Constraints

- Keep the modular monolith intact.
- PostgreSQL remains the only source of truth.
- Flyway remains the only schema-evolution mechanism.
- Do not duplicate inventory/production business rules in runtime code.
- Do not add third-party runtime infrastructure unless the issue/ADR is explicitly revised.
- Do not add a container registry requirement.
- Do not expose PostgreSQL to the LAN.
- Do not create public internet exposure or router port forwarding.
- Do not weaken authentication, CSRF, session, or same-origin behavior from #178/#179.

## Out of scope

Do not implement:

- cloud/provider deployment;
- public DNS/domain/TLS;
- remote internet access;
- router configuration;
- VPN/tunnel access;
- registry publication;
- Kubernetes/Terraform;
- reverse proxy as a third service without an ADR-level blocker;
- backup/restore workflow (#184);
- final go-live runbook (#185);
- OS-specific workstation startup/firewall/network/shortcut setup (#186);
- CI runtime validation workflow (#183);
- product/business feature changes;
- authentication redesign;
- automatic self-update.

## Stop conditions

Stop and report before expanding scope if any of these are discovered:

- Spring Boot cannot reliably serve the Angular production bundle under the existing router/security model;
- a third long-running service appears necessary;
- current #178/#179 security behavior conflicts with same-origin static serving;
- the operational runtime requires a host-specific mechanism that belongs to #186;
- a database/schema change appears necessary for runtime packaging;
- the existing API contract would need to change.

Do not silently work around these with a new architecture.

## Final implementation report

Report:

1. files created/modified;
2. runtime topology implemented;
3. application image/build decisions;
4. SPA/static-serving changes, if any;
5. operational profile/configuration changes;
6. secrets/external configuration behavior;
7. persistence/restart behavior;
8. tests added/updated;
9. `./mvnw verify` result and test total;
10. `pnpm lint` result;
11. `pnpm test` result and totals;
12. `pnpm build` result;
13. operational Docker Compose smoke results, including what was not run;
14. warnings/non-blocking observations;
15. acceptance criteria not satisfied, if any;
16. `git diff --check` result;
17. `git status --short`.
