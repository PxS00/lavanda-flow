# Issue #177 — Define v0.6.0 local-first readiness baseline

## Objective

Define and document the smallest architecture that allows Lavanda Flow to become Céu de Lavanda's real daily operational system with zero recurring infrastructure cost.

The initial deployment is intentionally local-first: one operator notebook hosts the application and PostgreSQL, while the operator uses the same system from that notebook or a tablet on the same trusted LAN. The v0.6.0 architecture must make that topology explicit, secure enough for the stated threat model, recoverable, restart-safe, and simple enough that the operator never needs development tooling for normal use.

This issue is documentation/architecture only. It records the durable decisions that unblock #178, #179, #182, #184, #185, #186, and #187. It does not implement authentication, containers, startup integration, backup scripts, workstation configuration, or product behavior.

## Source of truth

Apply, in order:

1. GitHub issue #177;
2. `AGENTS.md`;
3. `docs/product/scope-v1.md`;
4. `docs/architecture/architecture.md`;
5. `docs/architecture/dependencies.md`;
6. `docs/architecture/decisions/README.md`;
7. ADR 0001 (`docs/architecture/decisions/0001-use-modular-monolith.md`);
8. ADR 0004 (`docs/architecture/decisions/0004-use-postgresql-and-flyway.md`);
9. ADR 0009 (`docs/architecture/decisions/0009-define-v1-production-module-boundaries.md`);
10. `docs/development/git-workflow.md`;
11. this specification for the approved implementation details of #177.

Issue #177 remains authoritative for product intent, scope, acceptance criteria, constraints, and out-of-scope behavior. This specification records the concrete architecture decision already approved for v0.6.0 and the exact documentation delta required to express it.

## Branch

The documentation branch is:

```text
docs/177/define-local-first-readiness-baseline
```

It is based on the current `develop` branch and must target `develop` through the normal issue PR flow.

## Existing architecture to preserve

The following decisions remain unchanged:

- Lavanda Flow is a modular monolith;
- Angular is the frontend;
- Spring Boot is the backend;
- PostgreSQL is the only operational source of truth;
- Flyway owns schema evolution;
- the backend modules remain `catalog`, `inventory`, `production`, `suppliers`, and `shared`;
- security is cross-cutting and must not become a speculative business module or service;
- inventory and production business rules remain backend-authoritative;
- frontend/backend communication remains REST/JSON under `/api/v1`;
- quantities and inventory invariants are unchanged;
- normal issue work starts from `develop` and reaches it through squash merge.

No business module boundary, API route, DTO, enum, wire value, inventory rule, production rule, or persistence invariant changes in #177.

## Architectural problem

The current architecture documentation assumes `HTTPS / REST / JSON` in the high-level view and states that authentication is required before public exposure, but it deliberately leaves the actual operational deployment and authentication model undecided.

That ambiguity is no longer acceptable for v0.6.0 because Céu de Lavanda must begin using the system in daily operation.

The approved product constraints are now concrete:

- one primary operator;
- one notebook owned by the operator;
- optional tablet access on the same trusted local network;
- zero recurring infrastructure cost for the initial deployment;
- no need for public internet access in v0.6.0;
- no requirement for a public domain, hosted database, container registry, or cloud provider;
- the notebook may be powered off outside operating periods;
- tablet access is allowed to depend on that notebook being powered on, awake, and connected to the trusted LAN.

The architecture must optimize for those constraints rather than imitate a public SaaS deployment.

## Approved decision

Create ADR 0010:

```text
docs/architecture/decisions/0010-adopt-local-first-operator-hosted-runtime.md
```

ADR 0010 must be `Accepted` and dated with the issue implementation date. It establishes the following decisions.

### 1. Deployment model

Lavanda Flow v0.6.0 uses an **operator-hosted local-first deployment**.

```text
                    trusted local network

       ┌─────────────────┐       ┌─────────────────┐
       │ operator laptop │       │     tablet      │
       │     browser     │       │     browser     │
       └────────┬────────┘       └────────┬────────┘
                │                         │
                └────────────┬────────────┘
                             │ HTTP / same origin
                             ▼
                ┌─────────────────────────┐
                │   operator laptop host  │
                │                         │
                │  Lavanda Flow app       │
                │  Angular + Spring Boot  │
                │            │            │
                │            ▼            │
                │       PostgreSQL        │
                └─────────────────────────┘
```

The application is not publicly internet-accessible in v0.6.0.

Explicitly reject as v0.6.0 prerequisites:

- public cloud hosting;
- VPS hosting;
- managed PostgreSQL;
- domain purchase;
- public DNS;
- public TLS termination;
- Kubernetes;
- Terraform;
- container-registry publication;
- CDN;
- multi-instance/high-availability infrastructure.

These may be reconsidered only when a concrete later requirement justifies them.

### 2. Repository-owned runtime boundary

Use Docker Compose as the repository-owned operational runtime boundary because Docker/Docker Compose are already approved infrastructure and provide a deterministic way to package the existing monolith and PostgreSQL without requiring Maven, pnpm, Angular CLI, IntelliJ, or other development tooling during daily operation.

The operational topology is intentionally minimal:

```text
Docker Compose
├── lavanda-flow-app
└── postgres
```

Do not add a reverse-proxy container solely to mimic cloud topology.

The preferred v0.6.0 packaging decision is:

- Angular is built as production static assets;
- those assets are served by the Spring Boot application;
- Spring Boot serves both the SPA and `/api/v1` from one application process/origin;
- PostgreSQL runs as the second service;
- PostgreSQL uses persistent local storage;
- only the application HTTP entry point is published to the host/LAN;
- PostgreSQL is reachable only on the Compose-private network and is not host/LAN-published.

If #182 discovers a concrete technical blocker to serving the Angular production bundle from Spring Boot, that blocker must be surfaced rather than silently adding a third runtime service. Any durable topology change requires updating/superseding ADR 0010 as appropriate.

### 3. Host operating-system integration boundary

ADR 0010 must not invent the operator notebook's operating system when the repository does not contain that fact.

The architecture decision is therefore split intentionally:

- #177 selects Docker Compose plus container restart policy as the repository-owned runtime contract;
- #186 selects and verifies the actual OS-specific host integration on the real notebook.

The real workstation issue may use the smallest native mechanism appropriate to the actual host, for example Docker Engine/Desktop auto-start plus container restart policy, systemd, Task Scheduler, login startup integration, or an equivalent mechanism already available on that host.

The operator-facing invariant is fixed regardless of OS:

```text
power on notebook
      ↓
runtime becomes available automatically
      ↓
operator opens Lavanda Flow shortcut/browser entry
      ↓
uses application
```

Normal operation must not require the operator to open a terminal, repository, Docker UI, Maven, pnpm, or IDE.

### 4. Process lifecycle

The backend/database lifecycle is independent of the browser lifecycle.

Closing the browser must not stop the application or PostgreSQL.

The operational Compose services must use restart behavior suitable for recovery after Docker/runtime restart, preferably `restart: unless-stopped` unless #182 identifies a concrete runtime limitation.

The actual host must be configured in #186 so the container/runtime engine starts automatically according to the operator workflow.

The notebook being shut down, suspended, disconnected from Wi-Fi, or otherwise unavailable makes tablet access unavailable. This is an accepted v0.6.0 limitation, not a defect.

Do not globally disable notebook sleep or closed-lid suspend in repository configuration. #186 must test the real operator workflow and change host power behavior only if tablet access while the lid is closed is actually required.

### 5. Browser/API origin

The operational frontend and API use one local origin.

The frontend keeps the existing relative API boundary:

```text
/api/v1
```

Do not hard-code a public production hostname in frontend feature code.

The exact host address is workstation/network configuration, not application source code. #186 must provide a stable LAN endpoint using the simplest reliable mechanism available on the actual network, for example:

- DHCP reservation plus a stable private IP;
- a stable hostname/local DNS name;
- mDNS/local hostname only where it is proven reliable for both notebook and tablet.

The operator must not be expected to discover a changing IP address manually during normal use.

A configurable non-privileged HTTP port such as `8080` is acceptable for v0.6.0. A shortcut/bookmark hides that implementation detail from normal operator use.

### 6. Network exposure

The trusted LAN is the only remote network boundary supported in v0.6.0.

Repository/workstation configuration must enforce these principles:

- publish only the Lavanda Flow HTTP entry point needed by notebook/tablet clients;
- do not publish PostgreSQL port `5432` to the LAN;
- do not expose an internal secondary backend port if Angular and the API share the same Spring Boot process;
- do not configure router port-forwarding;
- do not expose Lavanda Flow directly to the public internet;
- host firewall rules should allow only the required application port from the trusted LAN when firewall configuration is needed.

Remote access from outside the trusted LAN is explicitly deferred. A later requirement should prefer a private VPN/tunnel design over raw router port-forwarding, but that is not part of v0.6.0.

### 7. Transport security

The v0.6.0 local operational baseline uses plain HTTP on the trusted LAN unless #186 can provide local HTTPS with no meaningful operational complexity.

Public HTTPS is not a prerequisite for first use because the application is not internet-facing.

This is an explicit threat-model trade-off, not an accidental omission.

Consequences:

- do not claim transport confidentiality against devices already capable of observing the trusted LAN;
- do not open the application to untrusted/public networks under this profile;
- if the deployment later moves to public/hosted access, HTTPS becomes mandatory before exposure;
- the security configuration must not set contradictory HTTPS-only cookie flags in the accepted HTTP profile.

### 8. Authentication model

Use Spring Security stateful server-side authentication.

Do not use JWT, OAuth, SSO, external identity providers, MFA, token refresh flows, or a complex RBAC model for v0.6.0.

The initial authorization model is intentionally simple:

- one or a very small number of operator accounts;
- every authenticated operator has the same operational capabilities;
- no public self-registration;
- no role hierarchy unless a later concrete requirement justifies it.

Authentication/session contracts are implemented by #178 and consumed by #179.

### 9. Session and cookie policy

The session identifier is a server-side session cookie.

For the accepted trusted-LAN HTTP operational profile:

- `HttpOnly = true`;
- `SameSite = Lax`;
- cookie path covers the application;
- `Secure = false` because browsers do not send `Secure` cookies over plain HTTP;
- use a finite idle timeout of 12 hours so one normal workday does not require repeated login while stale sessions do not remain indefinitely.

If HTTPS is enabled in a future profile or by a later deployment:

- `Secure = true` becomes mandatory;
- the application contracts do not change merely because transport security improves.

Do not persist session credentials or passwords in Angular `localStorage` or `sessionStorage`.

### 10. CSRF

CSRF protection remains enabled for state-changing browser requests.

Use the Spring Security/Angular same-origin XSRF pattern:

- backend provides the CSRF token in the mechanism selected by #178;
- Angular sends the token on state-changing same-origin requests using `HttpClient` integration;
- the session cookie remains `HttpOnly`;
- the CSRF token may use a separate browser-readable cookie where required by Spring Security/Angular integration;
- do not disable CSRF merely because the deployment is local.

Operational CORS does not need to permit arbitrary origins because the application is same-origin. Development may use an Angular proxy or narrowly scoped dev configuration without weakening the operational profile.

### 11. Authentication endpoint boundary

#178 may refine exact response shapes, but ADR 0010 must establish the boundary:

Public/minimum bootstrap HTTP contracts:

```text
POST /api/v1/auth/login
GET  /api/v1/auth/session
```

`GET /api/v1/auth/session` may report an unauthenticated state without exposing account-sensitive details.

Authenticated state-changing contract:

```text
POST /api/v1/auth/logout
```

All other operational `/api/v1/**` routes require authentication unless an explicit later contract documents a reason otherwise.

No public registration or password-reset endpoint is required for v0.6.0.

### 12. Operator credential provisioning

Initial operator creation is a maintainer operation, not an operator-facing registration flow.

The fixed semantics are:

- no username/password is committed in source code, Dockerfiles, Compose files, Flyway migrations, fixtures, or documentation;
- passwords are stored only as adaptive one-way hashes through Spring Security `PasswordEncoder`;
- account creation is performed through an explicit local/bootstrap maintenance mechanism implemented by #178;
- normal application startup must not silently create or overwrite an operator account;
- the bootstrap mechanism must be safe to rerun and must not reset an existing password merely because bootstrap variables remain present;
- plaintext bootstrap credentials must be removed from the runtime environment/configuration after successful provisioning where practical.

An in-application public registration flow, email reset flow, and forced first-login password change are not required for v0.6.0.

Password rotation/recovery may be implemented as a maintainer-only local operation in #178 if needed to make account recovery deterministic; do not add email/identity infrastructure.

### 13. Swagger/OpenAPI and management endpoints

The operational profile minimizes diagnostic exposure.

Decision:

- `/actuator/health` may remain reachable without authentication only if it returns minimal status and no sensitive details;
- detailed health information is not public;
- Swagger UI and OpenAPI JSON are disabled in the operational profile by default;
- Prometheus scraping is disabled/not LAN-exposed in the operational profile unless a concrete later monitoring requirement is approved;
- development profiles may keep documentation/metrics behavior needed by developers.

Do not remove the project's documented observability capability; constrain its operational exposure to the actual local use case.

### 14. Secrets and local configuration

Runtime secrets are external to tracked source and immutable image layers.

The repository may provide example/template configuration with placeholder names only.

The real workstation may use an untracked local environment file or equivalent host-supported mechanism selected by #186.

At minimum protect:

- PostgreSQL password;
- operator bootstrap credential material;
- any session/security secret introduced by implementation.

Do not require a paid secret manager for v0.6.0.

### 15. PostgreSQL persistence

PostgreSQL remains the source of truth.

The operational runtime must use persistent local storage/volume owned by the operator host. Routine operations such as:

```text
browser close
application restart
container restart
Docker/runtime restart
notebook reboot
application upgrade
```

must not delete business data.

PostgreSQL schema evolution remains exclusively Flyway-owned. Do not use Hibernate schema generation for operational installation/update.

### 16. Backup and recovery boundary

#177 establishes recovery as a go-live requirement; #184 implements the workflow.

The architecture requires:

- PostgreSQL-native logical backup;
- tested restore before go-live;
- backup before application/schema upgrades;
- a recurring backup baseline appropriate to the single-operator business;
- at least one current backup copy outside the notebook's primary storage/failure domain.

The off-notebook copy may use an existing zero-cost mechanism such as a cloud-drive folder already available to the operator, removable media, or another trusted device. Application code must not depend on a cloud-storage SDK solely for this purpose.

### 17. Release/install/update model

The Git repository and GitHub release/tag remain the immutable release source.

v0.6.0 does not require GHCR or another container registry.

The maintainer installs/updates the operator notebook from the exact released `vX.Y.Z` revision and builds/starts the operational runtime through the repository path defined by #182 and validated by #183.

Daily operator use must not require rebuilding anything.

Upgrade sequence must preserve:

1. current backup;
2. exact release revision;
3. deterministic runtime build/start;
4. Flyway forward migration;
5. restart/persistence smoke validation.

Automatic self-update is out of scope.

### 18. Future hosted deployment

The local-first decision must not contaminate business modules with host-specific logic.

A future hosted deployment should be able to keep:

- the same modular monolith;
- the same Angular application;
- the same `/api/v1` contracts;
- the same PostgreSQL/Flyway model;
- the same session-based auth contract if still appropriate.

Moving to hosted/public operation would primarily change runtime/network configuration:

- replace local PostgreSQL storage with appropriately secured hosted persistence if desired;
- add HTTPS/TLS;
- set `Secure` session cookies;
- introduce public DNS/domain only if required;
- reconsider remote-access threat model and operational backup ownership.

Do not pre-build any of that infrastructure in v0.6.0.

## Documentation delta

Issue #177 implementation must be narrowly limited to architecture documentation.

### Create

```text
docs/architecture/decisions/0010-adopt-local-first-operator-hosted-runtime.md
```

The ADR must follow `docs/architecture/decisions/README.md` and include:

- status/date;
- context;
- decision;
- runtime topology;
- network/transport/security decisions;
- lifecycle/persistence/recovery decisions;
- alternatives considered;
- positive consequences;
- trade-offs/risks;
- deferred decisions.

### Update

```text
docs/architecture/architecture.md
```

Update only the sections necessary to remove the current deployment/authentication ambiguity and align the high-level view with ADR 0010.

At minimum:

- distinguish the v0.6.0 local operational topology from a future public hosted topology;
- replace the unconditional `HTTPS` implication in the V1/local high-level diagram with the accepted local HTTP/same-origin boundary or otherwise explain the profiles explicitly;
- state that v0.6.0 uses an operator-hosted local runtime with Angular served by Spring Boot and PostgreSQL kept internal to the runtime;
- replace `authorization remains a deployment decision` with the accepted stateful operator-session baseline;
- update Security and observability to match the local threat model, CSRF/session policy, and management endpoint exposure;
- update the deliberately-open decisions so public/cloud deployment remains deferred rather than authentication itself.

Do not rewrite unrelated architecture sections.

### Do not change

Do not change in #177:

- production code;
- frontend code;
- `compose.yaml`;
- Dockerfiles;
- workflow files;
- Spring configuration;
- Flyway migrations;
- dependencies;
- API documentation;
- database schema;
- product feature documentation unless a directly contradictory architecture sentence must be corrected.

Those changes belong to their owning implementation issues.

## Alternatives to record in ADR 0010

ADR 0010 must document at least these alternatives and outcomes.

### Public cloud/VPS now

Rejected for v0.6.0.

It adds recurring cost and operations that the single-operator trusted-LAN use case does not require.

### Local notebook host

Accepted.

It satisfies the current operator workflow at zero recurring infrastructure cost and keeps data under direct business control, at the accepted cost that tablet availability depends on the notebook.

### Separate frontend reverse proxy container

Rejected for the initial runtime unless implementation proves Spring Boot static serving unsuitable.

A separate Nginx/Caddy container would add another always-on process and configuration boundary without a current need. The small local monolith benefits from one application HTTP process.

### JWT/token authentication

Rejected.

The browser and API share one origin and one server-side application. Stateful Spring Security sessions are simpler and avoid browser token-storage/refresh complexity.

### No authentication because the LAN is trusted

Rejected.

The application contains authoritative inventory and stock-changing operations, and multiple devices on the LAN can reach it. A simple authenticated session is proportionate and already supported by the approved Spring Security stack.

### Disable CSRF because the app is local

Rejected.

Session-cookie authentication still benefits from CSRF protection, and Angular/Spring provide a standard same-origin integration without meaningful architectural complexity.

### Require local HTTPS before first use

Rejected as a v0.6.0 blocker.

The application is intentionally not publicly exposed. Local certificate provisioning/trust on both notebook and tablet would add operational complexity disproportionate to the current threat model. HTTPS becomes mandatory before any future public exposure.

### Registry-published container images

Rejected as a v0.6.0 operational prerequisite.

CI reproducibility is required, but a registry is not needed when one maintainer installs one local host from an exact tagged release.

## Security consequences and accepted risk

The ADR must explicitly state the accepted residual risk of plain HTTP on a trusted LAN:

- credentials/session traffic are not encrypted against a malicious device capable of observing that LAN;
- this profile is acceptable only while the application remains on the trusted local network and is not port-forwarded/publicly exposed;
- strong Wi-Fi/router access control remains an environmental assumption;
- moving to an untrusted/public network requires a new transport/deployment decision with HTTPS before exposure.

Do not describe plain HTTP as equivalent security to HTTPS.

## Resource-use principle

The local operational runtime should remain lightweight for an ordinary operator notebook.

Architecture implications:

- two long-running services are the target: application and PostgreSQL;
- no reverse proxy solely for topology aesthetics;
- no Redis;
- no message broker;
- no observability sidecars;
- no orchestration platform;
- no continuous build process;
- no frontend dev server in daily operation.

#187 will observe actual idle resource behavior on the real workstation. #177 does not invent numeric RAM/CPU guarantees without measurement.

## Acceptance criteria mapping

The implementation is complete only when all of the following are true:

- ADR 0010 exists, is `Accepted`, and records the complete local-first runtime/security decision;
- the ADR explicitly documents zero recurring infrastructure cost as a v0.6.0 constraint;
- the ADR explicitly prohibits public internet exposure/router port forwarding in the supported profile;
- Docker Compose is documented as the repository-owned operational runtime contract;
- the two-service `lavanda-flow-app` + PostgreSQL topology is documented;
- Angular static serving by Spring Boot is the preferred one-process HTTP boundary;
- OS-specific boot integration is explicitly delegated to #186 rather than guessed;
- browser closure is explicitly independent from backend/database lifecycle;
- notebook sleep/shutdown/Wi-Fi limitations for tablet access are explicit;
- the stable-LAN-endpoint responsibility is explicit and delegated to #186;
- the stateful Spring Security session model is explicit;
- `HttpOnly`, `SameSite=Lax`, HTTP-profile `Secure=false`, HTTPS-profile `Secure=true`, and 12-hour idle timeout are explicit;
- CSRF remains enabled and Angular XSRF integration is documented;
- public registration/JWT/OAuth/SSO/MFA/complex RBAC are explicitly outside the baseline;
- operator bootstrap semantics are explicit and do not allow committed/default plaintext credentials;
- `/actuator/health` minimal exposure and operational Swagger/OpenAPI/Prometheus restrictions are explicit;
- PostgreSQL persistence and non-LAN exposure are explicit;
- backup/restore plus off-notebook copy is explicit;
- exact tagged-release installation/update is explicit and registry publication is not required;
- future cloud/public migration is described as a runtime/security-profile change rather than a business-module redesign;
- `docs/architecture/architecture.md` is aligned with ADR 0010 without unrelated rewrites;
- no production/config/runtime implementation file is changed;
- `git diff --check` succeeds.

## Validation

Because #177 is documentation-only, validation is intentionally focused.

Run at minimum:

```bash
git diff --check
```

Also review:

```bash
git diff -- docs/architecture/architecture.md docs/architecture/decisions/0010-adopt-local-first-operator-hosted-runtime.md
git status --short
```

Repository-quality/documentation checks that run automatically in CI must remain green.

Do not run or modify production code merely to satisfy this documentation issue.

## Out of scope

- implementing authentication/session endpoints;
- adding operator schema/migrations;
- adding Angular login UI;
- creating operational Docker/Compose files;
- changing runtime Spring configuration;
- configuring the real notebook;
- choosing the real notebook's OS-specific startup mechanism without evidence;
- implementing backup scripts;
- configuring firewall/DHCP/hostname on the real network;
- public internet access;
- public domain/DNS/TLS;
- VPN/remote access;
- cloud hosting;
- GHCR/container publishing;
- PWA/offline behavior;
- minimum-shelf-life policy #73;
- notifications, analytics, costs, sales, fiscal, purchasing, or other post-V1 features.

## Final implementation report

The implementation report for #177 must include:

1. files created/modified;
2. architecture decisions recorded;
3. alternatives explicitly rejected/deferred;
4. confirmation that no production code/configuration was changed;
5. `git diff --check` and repository-quality results;
6. any acceptance criterion not satisfied;
7. `git status --short`.
