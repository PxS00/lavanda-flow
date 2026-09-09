# Issue #186 — Prepare operator workstation and LAN access

## Status

Approved implementation specification for issue #186.

The GitHub issue remains the source of truth for objective, scope, acceptance criteria, constraints, and out-of-scope behavior. This document fixes the Windows 11 Home workstation integration decisions needed to implement and validate that issue without changing the accepted local-first application/runtime architecture.

## Objective

Prepare the real Céu de Lavanda Galaxy Book running Windows 11 Home as the initial Lavanda Flow operational host, using the existing local operational Docker Compose runtime, and prove reliable notebook/tablet access over the trusted LAN with no recurring infrastructure cost.

## Context

ADR 0010 selected a local-first operator-hosted topology and delegated real workstation integration to #186. Issues #178/#179 delivered stateful authenticated browser sessions, #182 delivered the two-service operational runtime, #183 validates that runtime in CI, and #184 delivered PostgreSQL-native backup and restore verification.

The real host is now known:

- Samsung Galaxy Book;
- Windows 11 Home;
- Docker Desktop using the WSL 2 backend;
- WSL 2 is infrastructure used by Docker Desktop, not an operator-facing Linux workstation environment;
- no Ubuntu or other general-purpose WSL distribution is required for normal Lavanda Flow operation;
- the operator continues to use Windows normally.

The accepted runtime topology remains unchanged:

```text
Windows 11 Home host
└── Docker Desktop (WSL 2 backend)
    └── Docker Compose project lavanda-flow-operational
        ├── lavanda-flow-app
        └── postgres
```

Only the application HTTP entry point is reachable from the trusted LAN. PostgreSQL remains private to the Compose network.

## Source of truth and relevant references

Read before implementation or workstation changes:

- GitHub issue #186;
- `AGENTS.md`;
- `docs/architecture/decisions/0010-adopt-local-first-operator-hosted-runtime.md`;
- `docs/operations/local-operational-runtime.md`;
- `docs/operations/postgresql-backup-restore.md`;
- `compose.operational.yaml`;
- `operational.env.example`.

No backend or frontend module implementation is expected for this issue.

## Scope decisions

### 1. Windows runtime prerequisite

Use Docker Desktop on Windows 11 Home with the WSL 2 backend.

WSL 2 is a host prerequisite for Docker Desktop only. Do not require the operator to install or use Ubuntu, a Linux shell, Maven, pnpm, an IDE, or repository tooling for daily operation.

The maintainer may use PowerShell during initial setup and maintenance.

Validate before installation/configuration:

- Windows virtualization support is enabled and available;
- WSL 2 platform support required by Docker Desktop is enabled;
- Docker Desktop can run Linux containers with the WSL 2 backend;
- Docker Compose is available through Docker Desktop.

Do not switch the application to native Windows PostgreSQL/Java services and do not introduce Hyper-V-only or Docker VMM-specific runtime architecture.

### 2. Operational source and installation location

Install the operational runtime from reviewed repository/release material corresponding to the exact intended release revision.

For #186 validation before the final v0.6.0 release exists, use the explicitly recorded tested `develop` revision. #185/#187/#188 will convert that into the final released `v0.6.0` installation/cutover flow.

Keep the operational checkout in a maintainer-controlled Windows directory. The operator must not need to browse or interact with it during daily use.

Do not copy the operational source tree inside a manually managed Linux distribution or require the operator to work from a WSL filesystem.

### 3. Runtime configuration and secrets

Create the ignored `.env.operational` from `operational.env.example` in the operational checkout.

Required values remain external to tracked source:

- `POSTGRES_DB`;
- `POSTGRES_USER`;
- `POSTGRES_PASSWORD`;
- `LAVANDA_HTTP_PORT` when overriding the default;
- `LAVANDA_SESSION_COOKIE_SECURE=false` for the accepted trusted-LAN HTTP profile;
- bootstrap variables only during explicit initial operator provisioning.

After initial operator bootstrap succeeds:

- disable bootstrap;
- remove the plaintext bootstrap password from workstation configuration where practical;
- never commit the file or copy its contents into documentation, screenshots, logs, issue comments, or GitHub artifacts.

Do not put secrets in Docker build arguments or immutable image layers.

### 4. Persistent PostgreSQL storage

Use the existing `postgres-data` named volume owned by the stable `lavanda-flow-operational` Compose project.

Do not bind PostgreSQL to a manually chosen host directory solely for Windows visibility. Docker Desktop owns the backing storage for the named volume.

Validate that business data survives:

- browser closure;
- `lavanda-flow-app` restart;
- normal Compose/runtime restart;
- Docker Desktop restart;
- full Windows reboot.

Never use `docker compose ... down -v` in normal operation or workstation preparation.

### 5. Automatic runtime startup

Use the smallest supported Windows integration:

1. Docker Desktop starts automatically when the operator signs in to Windows.
2. The existing Compose services retain `restart: unless-stopped`.
3. After the Docker engine becomes available, the existing `lavanda-flow-operational` containers return automatically without a maintainer opening a terminal or Docker UI.

The validated operational boot boundary is therefore:

```text
power on Galaxy Book
→ sign in to Windows
→ Docker Desktop starts automatically
→ Docker engine becomes ready
→ lavanda-flow-app and postgres return automatically
→ operator opens Lavanda Flow shortcut
```

Do not add a separate always-on Windows service, Kubernetes, custom process supervisor, or duplicated application startup mechanism unless this proven path fails on the real host.

If Docker Desktop does not reliably restore the existing Compose containers after sign-in, surface that as a blocker before adding another startup mechanism.

### 6. Operator launcher

Create a Windows operator-facing shortcut named `Lavanda Flow` that opens the stable application URL in the default supported browser.

The shortcut must not expose credentials and must not require the operator to:

- open PowerShell or Command Prompt;
- open Docker Desktop;
- navigate to the repository;
- run Maven or pnpm;
- start PostgreSQL manually.

The launcher opens the application only; runtime lifecycle remains independent of browser lifecycle.

### 7. Trusted LAN and stable endpoint

The Windows Wi-Fi/network profile used for operation must be explicitly treated as the trusted private LAN.

The tablet endpoint must be stable across normal DHCP renewals/reboots. Select the simplest mechanism proven on the actual network in this order of preference:

1. router DHCP reservation for the Galaxy Book's LAN address;
2. a stable local hostname/local DNS/mDNS mechanism that is proven to resolve reliably from the tablet;
3. a host static private IPv4 address only when it can be configured without conflicting with the router DHCP pool.

Do not hard-code the actual private address in application source code.

The final selected endpoint and any router-specific maintenance step belong in workstation/runbook documentation without credentials.

The default application port remains `8080` unless the real host has a concrete conflict.

### 8. Windows firewall

Use Windows Defender Firewall for the trusted-LAN boundary.

Allow inbound TCP access only to the configured Lavanda Flow application port for the trusted/private network scope. Prefer a rule limited to the Private profile and local subnet rather than a globally open inbound rule.

Do not create an inbound rule for PostgreSQL `5432` or any secondary/internal backend port.

Validate from another LAN client that:

- the application HTTP port is reachable while the host is awake and connected;
- PostgreSQL `5432` is not reachable;
- no unintended Lavanda Flow service port is reachable.

No router port forwarding, UPnP exposure, public DNS, public TLS endpoint, or internet-facing NAT rule is permitted.

### 9. Authentication and browser behavior

Validate the existing #178/#179 behavior from both notebook and tablet using the selected stable HTTP LAN endpoint:

- unauthenticated protected access is rejected;
- operator login succeeds;
- session survives normal browser navigation/reload within its valid lifetime;
- authenticated state-changing requests succeed with the existing CSRF/XSRF behavior;
- logout invalidates effective authenticated access.

Do not introduce CORS changes, JWT, browser token storage, or a second frontend origin for workstation convenience.

### 10. Sleep and closed-lid behavior

Start with the normal Windows power-management behavior. Do not globally disable sleep or closed-lid suspend merely to keep the application reachable.

Prove the real behavior on the Galaxy Book:

- while powered on, awake, and connected, notebook and tablet access works;
- when the notebook sleeps, shuts down, or loses Wi-Fi, tablet access becomes unavailable predictably;
- after wake/reconnect/reboot, the runtime returns according to the documented startup mechanism.

If the real business workflow specifically requires tablet access while the lid is closed, only then change the plugged-in lid action to keep the host awake and document the battery/thermal trade-off. Do not silently make this change.

### 11. Backup integration

Use #184 unchanged.

On the real host:

- create a successful logical backup using `scripts/operations/backup-postgres.sh`;
- verify the dump and checksum sidecar exist and are not tracked;
- choose one actual off-notebook destination with zero recurring infrastructure cost;
- copy both `.dump` and `.sha256` outside the Galaxy Book's primary failure domain;
- verify the copied checksum at the destination;
- record the chosen mechanism, not secret account details, for #185.

The destination may be an existing cloud-synced folder, removable media, or another trusted device/storage. No provider SDK is added to the application.

Disposable restore execution itself remains part of the #184 workflow and final #187 go-live validation; #186 must at least prove the real host can create the backup and maintain the off-notebook copy path.

### 12. Workstation evidence

Record maintainer-facing workstation facts needed by #185/#187 without committing secrets or sensitive network credentials.

Evidence should include:

- Windows edition and supported host description;
- Docker Desktop/WSL 2 runtime mechanism;
- tested repository/release revision;
- configured application port;
- stable endpoint mechanism and operator-facing URL;
- firewall rule intent/scope;
- autostart mechanism;
- observed reboot/startup behavior;
- tested sleep/lid behavior;
- persistence result;
- notebook/tablet authentication result;
- backup/off-notebook mechanism result;
- known non-blocking host limitations.

Avoid committing MAC addresses, router administrative credentials, database passwords, operator passwords, backup dumps, or real operational records.

## Implementation shape

This issue should remain infrastructure/operations focused.

Expected repository changes are limited to versioned documentation and, only if materially useful, small Windows-specific maintainer scripts that reduce setup error without becoming a second runtime implementation.

Do not add application code, database migrations, API changes, dependencies, or new Compose services.

Prefer recording concrete Windows setup steps in a focused operations document that #185 can later incorporate/reference rather than duplicating the final go-live runbook prematurely.

A likely documentation target is:

```text
docs/operations/windows-operator-workstation.md
```

Any optional PowerShell helper must be:

- maintainer-facing;
- non-secret;
- idempotent where practical;
- narrowly scoped;
- not required for normal operator use after setup.

Do not automate router administration or store router credentials.

## Validation plan

Validation must use the real Galaxy Book and tablet where specified. Repository-only checks cannot satisfy the hardware/network acceptance criteria.

### Host/runtime validation

- confirm Windows 11 Home and virtualization/WSL 2 prerequisites;
- confirm Docker Desktop uses the WSL 2 backend and Linux containers run;
- build/start the existing operational Compose runtime from the recorded revision;
- confirm exactly `lavanda-flow-app` and `postgres` are the long-running operational services;
- confirm PostgreSQL has no published host port;
- confirm the application is healthy.

### Startup/persistence validation

- close browser and confirm services remain running;
- reopen shortcut and confirm normal application access;
- restart `lavanda-flow-app` and confirm persisted data remains;
- restart Docker Desktop/runtime and confirm data remains;
- reboot Windows;
- sign in normally;
- without developer intervention, wait for Docker/runtime auto-recovery;
- open the operator shortcut and confirm application/data availability.

Use non-sensitive test evidence. Do not insert synthetic acceptance rows into the final production database unless explicitly approved; #187 owns final clean acceptance strategy.

### Network validation

From the tablet or another trusted LAN client:

- resolve/use the selected stable endpoint;
- access the Lavanda Flow HTTP port;
- authenticate successfully;
- perform representative non-destructive navigation;
- confirm `5432` is not reachable;
- confirm behavior when the host sleeps/disconnects/shuts down;
- confirm access returns after host wake/reconnect/reboot.

Verify router configuration has no Lavanda Flow/public port forwarding.

### Backup validation

- run the #184 backup workflow on the Galaxy Book;
- copy dump + checksum to the selected off-notebook destination;
- verify checksum after copy;
- ensure no dump or secret becomes tracked.

### Repository validation

For repository changes:

```text
git diff --check
```

Run repository-quality checks applicable to the files actually changed. Do not rerun backend/frontend suites merely for ceremony when no application code changed.

Before finishing:

```text
git diff
git diff --check
git status --short
```

## Acceptance mapping

The implementation is complete only when the real host proves all issue acceptance criteria, including:

- Windows 11 Home Galaxy Book is prepared under ADR 0010;
- Docker Desktop + WSL 2 backend provides the unchanged operational Compose runtime;
- startup after normal Windows sign-in requires no developer intervention;
- `Lavanda Flow` shortcut requires no terminal/Docker UI interaction;
- closing/reopening the browser does not affect service lifecycle;
- tablet uses a stable LAN endpoint;
- sleep/shutdown/disconnection limitations are observed and documented;
- database state survives runtime restart and full reboot;
- PostgreSQL is not LAN-reachable on `5432`;
- only the required application entry point is allowed by the local firewall;
- no router public exposure exists;
- secrets remain external and uncommitted;
- notebook and tablet authentication work using the existing session/CSRF contracts;
- #184 backup creation succeeds on the real host;
- an off-notebook copy mechanism is selected and tested;
- #185 can produce the final runbook without an undocumented critical workstation step.

## Constraints

- Preserve ADR 0010 and the two-service runtime from #182.
- Preserve #178/#179 authentication/session/CSRF behavior.
- Preserve #184 backup/restore behavior.
- Windows 11 Home is the supported initial host for this workstation profile.
- Docker Desktop uses WSL 2 internally; the operator is not required to use Linux.
- Zero recurring infrastructure cost remains mandatory.
- Do not expose the application or PostgreSQL to the public internet.
- Do not commit secrets, dumps, router credentials, operator passwords, or real production inventory rows.
- Do not silently add services, dependencies, host daemons, runtime variants, or product changes.

## Out of scope

- public/cloud deployment;
- public DNS/domain/TLS;
- internet-facing remote access;
- VPN/tunnel setup;
- Windows 11 Pro/Hyper-V migration;
- Docker VMM migration;
- native Windows PostgreSQL/Java runtime;
- Kubernetes or orchestration platforms;
- high availability/failover;
- PWA/offline behavior;
- product/application feature development;
- automated router administration;
- provider-specific cloud-backup SDKs.

## Dependencies and handoff

Completed prerequisites:

- #177 — local-first architecture baseline;
- #178 — operator authentication and secure sessions;
- #179 — authenticated frontend session;
- #182 — local operational runtime;
- #183 — CI validation of the runtime;
- #184 — local PostgreSQL backup/restore workflow.

This issue provides the real workstation/network facts required by:

- #185 — final local go-live runbook;
- #187 — final v0.6.0 go-live readiness validation;
- #188 — release and operator handoff.
