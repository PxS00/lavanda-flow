# ADR 0010 — Adopt a local-first operator-hosted runtime

- **Status:** Accepted
- **Date:** 2026-09-07

## Context

Lavanda Flow must become Céu de Lavanda's daily operational system without recurring infrastructure cost. The v0.6.0 use case has one primary operator, one operator-owned notebook, and optional tablet access from the same trusted LAN. It does not require public internet access, a public domain, cloud hosting, or high availability.

The existing modular monolith, Angular frontend, Spring Boot backend, PostgreSQL source of truth, Flyway schema ownership, module boundaries, REST/JSON `/api/v1` contracts, and inventory and production invariants remain unchanged. This decision defines only the runtime and security baseline that lets those existing components run in the stated environment.

## Decision

### Local-first runtime topology

v0.6.0 uses an operator-hosted local-first deployment. The application is not publicly internet-accessible and router port forwarding is not supported.

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

Docker Compose is the repository-owned operational runtime boundary. It provides deterministic daily operation without Maven, pnpm, Angular CLI, an IDE, or other development tooling. The target topology has two long-running services only:

```text
Docker Compose
├── lavanda-flow-app
└── postgres
```

Angular production static assets are built and served by Spring Boot, so the SPA and `/api/v1` use one application process and origin. Only that application HTTP entry point is published to the host/LAN. PostgreSQL uses persistent local storage and is reachable only on the Compose-private network, never as a host/LAN-published service.

No reverse-proxy container is added solely to imitate a hosted topology. If #182 proves that Spring Boot cannot serve the Angular production bundle, it must surface that blocker; it must not silently add a third service. Any durable topology change requires this ADR to be updated or superseded.

Public cloud or VPS hosting, managed PostgreSQL, domain purchase, public DNS, public TLS termination, Kubernetes, Terraform, container-registry publication, CDN, and multi-instance/high-availability infrastructure are not v0.6.0 prerequisites. They may be reconsidered only for a concrete later requirement.

### Host integration and lifecycle

#177 selects Docker Compose and a container restart policy as the repository-owned contract. Operational services should use `restart: unless-stopped` unless #182 identifies a concrete runtime limitation. #186 owns selection and verification of the real notebook's OS-specific integration, such as Docker Engine/Desktop auto-start plus restart policy, systemd, Task Scheduler, or login startup integration. This ADR does not infer the notebook operating system.

The fixed operator-facing workflow is:

```text
power on notebook
      ↓
runtime becomes available automatically
      ↓
operator opens Lavanda Flow shortcut/browser entry
      ↓
uses application
```

Normal operation must not require a terminal, repository, Docker UI, Maven, pnpm, or IDE. Browser closure is independent of the backend and database lifecycle; closing it must not stop either service.

The notebook may be powered off outside operating periods. Shutdown, suspend, Wi-Fi disconnection, or other notebook unavailability makes tablet access unavailable; this is an accepted v0.6.0 limitation. Repository configuration must not globally disable sleep or closed-lid suspend. #186 must test the real workflow and change host power behavior only if tablet access while the lid is closed is required.

The exact LAN address is workstation/network configuration, not application source code. #186 must provide a stable endpoint using the simplest proven mechanism on the real network, such as a DHCP reservation with stable private IP, stable hostname/local DNS, or reliable mDNS. Operators must not manually discover a changing IP during normal use. A configurable non-privileged HTTP port such as `8080` is acceptable; a shortcut or bookmark hides it.

### Network and transport profile

The trusted LAN is the only remote network boundary supported in v0.6.0. Runtime and workstation configuration must publish only the application HTTP entry point needed by notebook/tablet clients; they must not publish PostgreSQL port `5432` or a secondary backend port. They must not configure router port forwarding or public exposure. If firewall configuration is needed, it should allow only the required application port from the trusted LAN.

The accepted local operational profile uses plain HTTP on that trusted LAN unless #186 can provide local HTTPS without meaningful operational complexity. Public HTTPS is not a blocker for first use because this application is not internet-facing. This is a threat-model trade-off: credentials and sessions are not encrypted against a malicious device capable of observing the LAN. Strong Wi-Fi and router access control are environmental assumptions. Plain HTTP is not equivalent to HTTPS, and any untrusted, remote, or public deployment requires a new transport/deployment decision with HTTPS before exposure.

### Authentication and browser boundary

v0.6.0 uses Spring Security stateful server-side authentication for one or a very small number of operator accounts with the same operational capabilities. It has no public self-registration, role hierarchy, JWT, OAuth, SSO, external identity provider, MFA, or token-refresh flow. The trusted LAN does not remove the need for authentication because inventory operations are authoritative and multiple LAN devices can reach the application.

The session identifier is a server-side session cookie with `HttpOnly=true`, `SameSite=Lax`, an application-wide path, and a finite 12-hour idle timeout. In the accepted HTTP profile, `Secure=false` because browsers do not send secure cookies over plain HTTP. In a future HTTPS profile, `Secure=true` is mandatory without changing application contracts. Angular must not persist credentials or sessions in `localStorage` or `sessionStorage`.

CSRF protection remains enabled for state-changing browser requests. #178 will select the Spring Security mechanism that provides the CSRF token and #179 will use Angular `HttpClient`'s same-origin XSRF integration. The session cookie remains `HttpOnly`; a separate browser-readable CSRF-token cookie is allowed where that standard integration requires it. Operational CORS does not permit arbitrary origins.

#178 may refine response shapes, but the public/minimum bootstrap boundary is:

```text
POST /api/v1/auth/login
GET  /api/v1/auth/session
```

`GET /api/v1/auth/session` may return an unauthenticated state without account-sensitive details. `POST /api/v1/auth/logout` is authenticated and state-changing. All other operational `/api/v1/**` routes require authentication unless a later explicit contract documents otherwise. Public registration and password-reset endpoints are not required.

Initial operator creation is a maintainer-only local/bootstrap operation implemented by #178. No username or password is committed in source, Dockerfiles, Compose files, Flyway migrations, fixtures, or documentation. Passwords use adaptive one-way hashes through Spring Security `PasswordEncoder`. Bootstrap must be explicitly rerunnable, never silently create or overwrite an account during normal startup, never reset an existing password from retained variables, and remove plaintext bootstrap credentials from runtime configuration where practical. In-application registration, email reset, and forced first-login password change are out of scope; maintainer-only local recovery may be added if needed.

### Operational exposure, persistence, recovery, and releases

`/actuator/health` may be unauthenticated only when it exposes minimal status and no sensitive details. Detailed health data is not public. Swagger UI, OpenAPI JSON, and Prometheus scraping are disabled or not LAN-exposed in the operational profile by default; development profiles may retain developer documentation and metrics. This constrains exposure without removing the documented observability capabilities.

Runtime secrets remain external to tracked source and immutable image layers. The repository may provide templates containing placeholder names only; #186 chooses the real untracked local environment file or equivalent host mechanism. PostgreSQL passwords, bootstrap credential material, and any session/security secret require protection. A paid secret manager is not required.

PostgreSQL persistent storage belongs to the operator host. Browser closure, application/container/runtime restart, notebook reboot, and application upgrade must not delete business data. PostgreSQL remains the source of truth, and Flyway remains the exclusive operational schema-evolution mechanism; Hibernate schema generation is not used for installation or updates.

Recovery is a go-live requirement, implemented by #184: PostgreSQL-native logical backup, a tested restore before go-live, a backup before application/schema upgrades, a recurring single-operator backup baseline, and at least one current copy outside the notebook's primary storage/failure domain. The off-notebook copy may use an existing cloud-drive folder, removable media, or another trusted device; application code does not need a cloud-storage SDK.

The Git repository and GitHub release/tag remain the immutable release source. A maintainer installs or updates the notebook from the exact released `vX.Y.Z` revision, then follows the deterministic runtime path defined by #182 and validated by #183. Upgrades preserve a current backup, exact release revision, deterministic build/start, Flyway forward migration, and restart/persistence smoke validation. Daily operation does not rebuild anything. Automatic self-update and registry publication are not required.

### Future hosted deployment

A future hosted/public deployment keeps the modular monolith, Angular application, `/api/v1` contracts, PostgreSQL/Flyway model, and—if still suitable—the session-authentication contract. It primarily changes runtime and network configuration: secured hosted persistence if desired, HTTPS and `Secure` cookies, public DNS/domain only if required, and a reassessed remote-access threat model and backup ownership. v0.6.0 does not pre-build those concerns or add host-specific logic to business modules.

## Consequences

### Positive

- zero recurring infrastructure cost for the stated single-operator workflow;
- a deterministic two-service runtime with no frontend dev server, reverse proxy, Redis, message broker, observability sidecar, orchestration platform, or continuous build process;
- one same-origin browser/API boundary with standard Spring Security and Angular XSRF support;
- PostgreSQL remains private to the runtime and persistent on the operator host;
- later hosted deployment remains a runtime/security-profile change rather than a business-module redesign.

### Trade-offs and risks

- tablet availability depends on the notebook being powered on, awake, and connected to the trusted LAN;
- plain HTTP on the trusted LAN does not protect credentials or sessions from a malicious LAN observer;
- the operator host owns local runtime availability, storage, and backup execution;
- this profile must not be reused for public or untrusted-network exposure without HTTPS and a new threat-model decision.

## Alternatives considered

### Public cloud or VPS now

Rejected for v0.6.0. It adds recurring cost and operations that the single-operator trusted-LAN use case does not require.

### Local notebook host

Accepted. It satisfies the current workflow at zero recurring infrastructure cost and keeps data under direct business control, accepting notebook-dependent tablet availability.

### Separate frontend reverse-proxy container

Rejected unless implementation proves Spring Boot static serving unsuitable. Nginx or Caddy would add an always-on process and configuration boundary with no current need.

### JWT or token authentication

Rejected. One browser origin and one server-side application make stateful Spring Security sessions simpler and avoid browser token-storage and refresh complexity.

### No authentication because the LAN is trusted

Rejected. The application makes authoritative stock changes and is reachable by multiple LAN devices.

### Disable CSRF because the application is local

Rejected. Session-cookie authentication still benefits from CSRF protection, and Spring Security plus Angular provide a standard same-origin integration.

### Require local HTTPS before first use

Rejected as a v0.6.0 blocker. Local certificate provisioning and trust on notebook and tablet add disproportionate operational complexity; HTTPS is mandatory before future public exposure.

### Registry-published container images

Rejected as an operational prerequisite. CI reproducibility is required, but one maintainer can install one local host from an exact tagged release without a registry.

## Deferred decisions

- #178 implements authentication, session, CSRF, bootstrap, and recovery contracts;
- #179 consumes those contracts in the frontend;
- #182 defines the repository runtime implementation and must surface any static-serving blocker;
- #183 validates the local operational runtime in CI, #184 implements backup/restore, #185 documents the local go-live runbook, #186 prepares the real workstation/OS/network integration, and #187 performs final local go-live validation including resource observations;
- remote access should prefer a private VPN/tunnel over raw router port forwarding if later required;
- public/cloud deployment, public DNS/TLS, high availability, PWA/offline behavior, and numeric resource guarantees remain outside v0.6.0.