# Issue #185 — Document local go-live runbook

## Status

Approved implementation specification for issue #185.

The GitHub issue remains the source of truth for objective, scope, acceptance criteria, constraints, and out-of-scope behavior. This specification fixes the concrete local-first runbook structure and operational decisions needed to document the v0.6.0 single-notebook deployment without introducing product, schema, dependency, or runtime changes.

## Objective

Create one authoritative maintainer-facing local go-live runbook for installing, provisioning, starting, validating, backing up, recovering, updating, and maintaining Lavanda Flow on the prepared Céu de Lavanda operator notebook.

The runbook must preserve the operator-facing boundary already proven by #186:

```text
power on Galaxy Book
→ sign in to Windows
→ Docker Desktop/runtime recover automatically
→ open the Lavanda Flow shortcut
→ use the notebook or tablet on the trusted LAN
```

Normal operator use must not require PowerShell, Git Bash, Docker Desktop UI, repository navigation, Docker commands, Maven, pnpm, an IDE, or other development tooling.

## Source of truth

Apply, in order:

1. GitHub issue #185;
2. `AGENTS.md`;
3. ADR 0010: `docs/architecture/decisions/0010-adopt-local-first-operator-hosted-runtime.md`;
4. `docs/operations/local-operational-runtime.md`;
5. `docs/operations/windows-operator-workstation.md`;
6. `docs/operations/postgresql-backup-restore.md`;
7. `docs/operations/initial-inventory-import.md`;
8. `docs/specs/0136-import-initial-inventory-snapshot.md`;
9. `docs/specs/0168-reconcile-final-snapshot-withdrawals.md`;
10. `docs/development/git-workflow.md`;
11. `compose.operational.yaml`;
12. `operational.env.example`;
13. current implementation only where needed to confirm existing behavior.

Do not invent new operational behavior in documentation. If the existing runtime cannot support a documented procedure safely, report the blocker instead of changing production code inside #185.

## Branch

```text
docs/185/document-local-go-live-runbook
```

The branch starts from `develop` after #186 was squash-merged at:

```text
ee73d0d5b1adafe196ff37016a6e629321b86155
```

## Dependency state

Completed prerequisites:

- #177 — local-first architecture baseline;
- #178/#179 — operator authentication and authenticated frontend session;
- #182 — local operational runtime;
- #183 — CI runtime validation;
- #184 — PostgreSQL backup/restore workflow;
- #186 — real Windows workstation and trusted-LAN preparation.

#185 is therefore unblocked. #187 will execute the documented runbook as part of final pre-release acceptance, and #188 will use it for the exact `v0.6.0` release cutover.

## Existing operational facts to preserve

The runbook must reflect the real supported environment validated by #186:

- Samsung Galaxy Book;
- Windows 11 Home;
- Docker Desktop with WSL 2 backend;
- no operator-facing Ubuntu/general-purpose WSL distribution;
- Docker Compose project `lavanda-flow-operational`;
- exactly two long-running services: `lavanda-flow-app` and `postgres`;
- PostgreSQL data in the named `postgres-data` volume;
- PostgreSQL port `5432` not published to the host/LAN;
- application HTTP port `8080`;
- stable trusted-LAN URL `http://192.168.15.12:8080` via router DHCP reservation;
- Windows Defender Firewall allows TCP 8080 only on the Private profile / `LocalSubnet`, with edge traversal blocked;
- no port forwarding, DMZ, UPnP, DDNS, or public exposure;
- Docker Desktop starts at Windows sign-in and existing `restart: unless-stopped` services recover automatically;
- desktop shortcut `Lavanda Flow` opens the stable LAN URL;
- closing the browser does not stop application/database services;
- on battery, closing the lid suspends the notebook;
- while plugged in, closing the lid does nothing so tablet access remains available;
- when the host is asleep, shut down, or disconnected from Wi-Fi, tablet access is intentionally unavailable;
- Google Drive is the selected zero-recurring-cost off-notebook backup mechanism;
- the current workstation operational database has no real inventory/production rows yet;
- `localhost:8080` was unreliable on the validated host while `127.0.0.1:8080` worked; operator access uses the stable LAN URL.

Do not copy secrets, MAC addresses, router credentials, Google account identifiers, backup dumps, or real business row contents into the runbook.

## Runbook authority and file placement

Create one primary authoritative runbook under:

```text
docs/operations/local-go-live-runbook.md
```

The runbook may reference narrower durable documents for deeper procedures, but it must contain enough sequencing and commands that #187/#188 do not depend on undocumented critical steps.

Existing documents remain focused references:

- `local-operational-runtime.md` — repository runtime boundary;
- `windows-operator-workstation.md` — validated workstation/network evidence;
- `postgresql-backup-restore.md` — backup/restore contract;
- `initial-inventory-import.md` — source-specific one-time migration contract.

Do not rewrite those documents wholesale. Update a reference document only when a small consistency correction is required to make the final runbook accurate.

## Audience boundary

The runbook is maintainer-facing engineering documentation in English.

Clearly distinguish:

### Operator daily flow

The operator should normally do only this:

1. power on the Galaxy Book;
2. sign in to Windows;
3. wait for the local runtime to recover;
4. open the `Lavanda Flow` shortcut;
5. sign in to Lavanda Flow;
6. use the notebook or tablet while on the trusted LAN.

### Maintainer-only procedures

Installation, release selection, `.env.operational`, bootstrap provisioning, lifecycle commands, backup, restore, import, update, logs, health checks, and recovery are maintainer tasks.

Do not tell the operator to use Docker, Git, PowerShell, Git Bash, Maven, pnpm, or repository tooling during normal daily use.

## Exact release selection

The final production installation must use an immutable Git release tag, never an arbitrary `develop`/issue/release branch.

For the v0.6.0 cutover, the accepted production source is:

```text
v0.6.0
```

The tag does not exist until #188 publishes the release. Therefore distinguish clearly between:

- pre-release validation in #187, which may use an explicitly recorded candidate commit from `develop`;
- production cutover in #188, which must install/update from the exact `v0.6.0` tag after it exists.

Document a maintainer flow equivalent to:

```powershell
git fetch --tags origin
git switch --detach v0.6.0
git rev-parse HEAD
git status --short
```

The recorded commit SHA must be captured during cutover evidence. Do not instruct production operation from a floating branch.

For later releases, use the same `vX.Y.Z` exact-tag rule.

## Supported host prerequisites

Document the concrete host prerequisites:

- Windows 11 Home on the prepared Galaxy Book;
- hardware virtualization enabled;
- WSL 2 support available for Docker Desktop;
- Docker Desktop configured for Linux containers with WSL 2 backend;
- Docker Compose available through Docker Desktop;
- Git available for maintainer release selection/update;
- trusted Wi-Fi configured as Windows Private network;
- DHCP reservation/firewall/shortcut already prepared according to `windows-operator-workstation.md`.

Do not require a host Java installation, Maven, pnpm, Angular CLI, native PostgreSQL client, Kubernetes, Hyper-V-only runtime, or a general-purpose Ubuntu distribution for normal production operation.

## Operational checkout

Use the existing maintainer-controlled Windows checkout convention rather than an operator-facing workspace.

Prefer examples based on:

```text
%USERPROFILE%\LavandaFlow\app
```

Do not hard-code a personal Windows account name into the runbook.

Generated `.env.operational`, backup artifacts, and external migration CSV files remain outside tracked source.

## Required configuration and secrets

Name every input from `operational.env.example` and explain its purpose without real values:

```text
POSTGRES_DB
POSTGRES_USER
POSTGRES_PASSWORD
LAVANDA_HTTP_PORT
LAVANDA_SESSION_COOKIE_SECURE
EXPIRATION_ALERT_WINDOW_DAYS
LAVANDA_SECURITY_BOOTSTRAP_ENABLED
LAVANDA_SECURITY_BOOTSTRAP_USERNAME
LAVANDA_SECURITY_BOOTSTRAP_PASSWORD
```

Requirements:

- `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` are mandatory operational PostgreSQL settings;
- `LAVANDA_HTTP_PORT` defaults to `8080`;
- `LAVANDA_SESSION_COOKIE_SECURE=false` is correct only for the accepted trusted-LAN plain-HTTP profile;
- `EXPIRATION_ALERT_WINDOW_DAYS` remains externally configurable;
- bootstrap is normally disabled;
- bootstrap username/password exist only during explicit initial operator provisioning;
- no default operator credential is documented;
- plaintext bootstrap password is removed from workstation configuration after successful provisioning where practical;
- `.env.operational` must stay ignored/uncommitted;
- never place secrets in Docker build arguments, documentation, screenshots, GitHub, or immutable image layers.

## First installation and Flyway

The runbook must document first installation from the exact release tag using the existing Compose runtime.

At minimum:

1. select the exact release tag;
2. create `.env.operational` from `operational.env.example` and fill required external values;
3. build the application image locally from that exact release;
4. start the existing two-service Compose runtime;
5. wait for PostgreSQL health and application startup;
6. verify minimal application health;
7. confirm no unexpected third service or PostgreSQL host port exists.

Use commands equivalent to:

```powershell
docker compose -f compose.operational.yaml --env-file .env.operational build
docker compose -f compose.operational.yaml --env-file .env.operational up -d
docker compose -f compose.operational.yaml --env-file .env.operational ps
```

Fresh PostgreSQL startup expectations must state explicitly:

- Flyway creates/applies the complete schema automatically from tracked migrations;
- PostgreSQL is the schema/data source of truth;
- Hibernate validates schema and must not be used to install/evolve it;
- no manual SQL should be required for normal installation;
- a migration failure is a blocker; do not bypass Flyway or edit historical migrations on the workstation.

Use `http://127.0.0.1:8080/actuator/health` for the validated host-local health example unless the configured port differs. The stable LAN URL remains the normal operator/tablet entry point.

## Initial operator provisioning

Document a safe one-time bootstrap procedure using the existing #178 mechanism.

Required sequence:

1. choose the real operator username/password outside the repository;
2. temporarily set bootstrap enabled plus username/password in `.env.operational`;
3. start or recreate `lavanda-flow-app` so the bootstrap runs;
4. verify that the operator can sign in through the normal Lavanda Flow UI;
5. set `LAVANDA_SECURITY_BOOTSTRAP_ENABLED=false`;
6. clear the plaintext bootstrap username/password configuration;
7. recreate the application container so hardened configuration is active;
8. verify the persisted operator account still signs in.

Use an existing runtime command equivalent to:

```powershell
docker compose -f compose.operational.yaml --env-file .env.operational up -d --force-recreate lavanda-flow-app
```

Do not claim that retained bootstrap variables reset an existing password. Do not document a committed default username/password or an in-application public registration path.

## Conditional initial inventory migration

The initial CSV workflow is migration-only and must never become recurring synchronization.

Document that it runs only when all of the following are true:

- the real operational database has not already received the approved initial inventory snapshot;
- the catalog remains uninitialized according to the importer guard;
- the frozen external CSV is the approved cutover source;
- the maintainer has explicitly decided initial migration is required.

The existence of Flyway history or an operator account does not itself mean the catalog is initialized.

The runbook must require:

1. verify the approved external CSV/checksum using `initial-inventory-import.md`;
2. use the approved explicit effective date from that migration document;
3. stop normal operator use during the migration window;
4. run `DRY_RUN` first against the external file;
5. inspect the deterministic report and require `rejected=0`;
6. verify the source checksum again before apply;
7. run `APPLY` only against the importer-accepted uninitialized target;
8. verify catalog/current stock/batches/history afterward;
9. never run the importer again once the operational catalog is initialized;
10. never treat the CSV/spreadsheet as runtime synchronization.

Prefer the already-built operational application image for v0.6.0 so the operator notebook does not need host Maven/Java solely for migration. The runbook may use a one-off Compose `run` with an explicit read-only bind mount for the external CSV and command-line properties appended to the existing image entrypoint, provided #187 proves the command on the real/prepared environment.

Do not modify Compose or application code merely to create a migration service in #185.

If a safe containerized invocation cannot be proven from the existing runtime, retain/reference the already supported `initial-inventory-import.md` procedure and report the host-tooling requirement explicitly for #187 instead of inventing an unvalidated command.

Preserve #136/#168 semantics: the final optional `Retirada` field adjusts the opening quantity only; it does not reconstruct historical withdrawal movements.

## Automatic startup and operator shortcut

Document the tested Windows startup chain:

```text
Windows sign-in
→ Docker Desktop auto-start
→ Docker engine ready
→ lavanda-flow-operational containers recover via restart: unless-stopped
→ operator opens Lavanda Flow shortcut
```

Normal browser closure does not stop the runtime.

The operator shortcut is:

```text
Lavanda Flow
```

and opens:

```text
http://192.168.15.12:8080
```

Do not tell the operator to start containers manually each day.

## Trusted LAN, firewall, and power behavior

Document the #186 network contract without exposing sensitive router details:

- notebook and tablet must be on the trusted LAN;
- Windows network profile is Private;
- stable address uses DHCP reservation;
- only TCP 8080 is allowed inbound from `LocalSubnet` on the Private profile;
- PostgreSQL `5432` remains unpublished and has no Windows Firewall allow rule;
- port forwarding is empty;
- DMZ, UPnP, and DDNS are disabled;
- no public internet exposure is supported;
- plain HTTP is accepted only for this trusted-LAN profile.

Document availability behavior:

- browser closed: runtime remains available;
- notebook awake/connected: notebook and tablet can use the application;
- notebook asleep/offline/Wi-Fi disconnected: tablet cannot use the application;
- closing lid on battery: host sleeps;
- closing lid while plugged in: host remains awake and tablet access remains available.

## Normal maintainer lifecycle commands

Provide concise commands from the operational checkout for:

### Status

```powershell
docker compose -f compose.operational.yaml --env-file .env.operational ps
```

### Start

```powershell
docker compose -f compose.operational.yaml --env-file .env.operational up -d
```

### Stop without deleting data

```powershell
docker compose -f compose.operational.yaml --env-file .env.operational down
```

### Restart application only

```powershell
docker compose -f compose.operational.yaml --env-file .env.operational restart lavanda-flow-app
```

### Full runtime restart while preserving data

Use a documented safe `down` followed by `up -d`, or an equivalent non-destructive Compose restart path.

Prominently prohibit:

```text
docker compose ... down -v
```

because it removes the PostgreSQL named volume.

## Logs and health

Document safe diagnostic commands without exposing secret values or business row contents.

At minimum:

```powershell
docker compose -f compose.operational.yaml --env-file .env.operational ps
docker compose -f compose.operational.yaml --env-file .env.operational logs --tail=200 lavanda-flow-app
docker compose -f compose.operational.yaml --env-file .env.operational logs --tail=200 postgres
```

Use the minimal health endpoint:

```text
http://127.0.0.1:8080/actuator/health
```

Expected result is aggregate `UP` only. Do not suggest exposing detailed Actuator, Swagger/OpenAPI, Prometheus, PostgreSQL, or development endpoints to the LAN.

When collecting logs for support, instruct the maintainer to review/redact them before sharing and never include `.env.operational`.

## Backup baseline

Do not duplicate the entire #184 document. Summarize and link the authoritative backup procedure.

The runbook must state:

- create one successful backup on every operating day with business-data changes, after final expected changes;
- create an additional backup immediately before every application/schema upgrade;
- keep at least the 7 most recent successful daily backups on the notebook while disk capacity is normal;
- keep one weekly backup for each of the previous 4 weeks outside the notebook failure domain;
- after each operating-day backup, ensure at least one current verified off-notebook copy exists;
- Google Drive is the currently selected off-notebook mechanism on the real workstation;
- copy both `.dump` and `.dump.sha256`;
- checksum verification is required after transfer;
- dumps are sensitive and SHA-256 provides integrity, not encryption.

Normal backup command:

```bash
scripts/operations/backup-postgres.sh
```

Because this script is Bash, document the validated Windows maintainer execution boundary (Git Bash) without making Git Bash part of normal operator use.

## Upgrade procedure

Document a conservative forward-only release update.

Required sequence:

1. confirm the intended exact `vX.Y.Z` tag/release;
2. create and verify a pre-upgrade backup;
3. verify/copy the backup off the notebook;
4. record current release tag/SHA;
5. fetch tags and switch the operational checkout to the new exact tag;
6. confirm `.env.operational` remains present/untracked;
7. rebuild the application image from the new exact tag;
8. start/recreate the runtime without deleting the PostgreSQL volume;
9. allow Flyway to validate existing history and apply only legitimate forward migrations;
10. verify application health;
11. verify operator login from notebook and tablet;
12. run representative non-destructive inventory/production reads;
13. confirm persisted state is present;
14. inspect logs for migration/startup errors;
15. keep the pre-upgrade backup until post-upgrade validation is accepted.

Do not run the initial CSV importer during normal upgrades.

Do not use `down -v`.

## Smoke checks after start/restart/upgrade

Keep checks non-destructive unless a later acceptance task explicitly owns write validation.

At minimum verify:

- `docker compose ... ps` shows expected services running/healthy;
- `/actuator/health` reports `UP`;
- operator login succeeds;
- dashboard loads;
- catalog/item search works;
- current stock/batch visibility works;
- movement/history read works;
- production/formula/execution visibility works when data exists;
- tablet reaches the stable LAN URL and authenticates;
- PostgreSQL remains unpublished to the host/LAN;
- no router public exposure was introduced.

#187 owns broader read/write acceptance against isolated data where needed.

## Recovery and restore

The runbook must reference `postgresql-backup-restore.md` as authoritative for restore.

Distinguish clearly:

1. disposable restore verification — safe acceptance/maintenance verification;
2. destructive operational recovery — maintainer procedure with downtime.

State explicitly:

- verify checksum before restore;
- prefer disposable restore verification before destructive recovery;
- stop `lavanda-flow-app` before changing the operational database;
- restore only into an explicitly empty recreated target database;
- preserve potentially recoverable failed data/volume until recovery succeeds where practical;
- use the intended exact release revision against the restored database;
- Flyway may validate/apply legitimate forward migrations only;
- verify health and representative records before returning to operation;
- never use `docker compose down -v` as a recovery shortcut.

## Rollback limitations

Document rollback accurately:

- application releases and database schema do not have an automatic downgrade path;
- Flyway migrations are forward-oriented and historical migrations are never edited to simulate rollback;
- switching the application binary/image back to an older release after a newer schema migration may be incompatible;
- if an upgrade must be reversed after schema/data changes, recovery may require downtime and restoration of the verified pre-upgrade backup using the compatible intended release;
- data entered after the restored backup may need manual reconstruction;
- do not claim zero-downtime rollback, database downgrade, PITR, replication, or high availability.

## Operator handoff and source-of-truth cutover

The runbook must contain an explicit handoff checklist.

Before declaring go-live:

- exact release tag/SHA recorded;
- runtime starts automatically after reboot;
- shortcut opens normally;
- notebook/tablet login works;
- stable LAN access works;
- host sleep/lid limitation explained;
- PostgreSQL remains private;
- current backup exists;
- off-notebook checksum-verified copy exists;
- operator credentials delivered through a secure channel outside the repository;
- initial inventory migration completed if genuinely required;
- representative smoke checks accepted.

Then record the cutover decision explicitly:

```text
Lavanda Flow/PostgreSQL becomes the operational source of truth.
The spreadsheet/CSV is frozen as historical migration evidence and must no longer be maintained in parallel.
```

Do not describe an indefinite dual-write or spreadsheet synchronization period after accepted cutover.

## Implementation boundary

This issue is documentation-only.

Expected changes:

```text
docs/specs/0185-document-local-go-live-runbook.md
docs/operations/local-go-live-runbook.md
```

A very small update to another existing operations document is allowed only when required to remove a real contradiction discovered while implementing the runbook.

No changes are expected in:

- backend Java;
- frontend Angular/TypeScript;
- Flyway migrations;
- `compose.operational.yaml`;
- Dockerfile;
- dependencies;
- API contracts;
- runtime business configuration defaults.

If a production/runtime change appears necessary for the runbook to be truthful, stop and report the blocker rather than silently expanding #185.

## Validation

Because this is documentation-only, do not run backend/frontend suites merely as ceremony unless product/runtime files unexpectedly change.

Required repository validation:

```text
git diff
git diff --check
git status --short
```

Also review the runbook for:

- secret values;
- personal account identifiers;
- MAC/router credentials;
- real database dumps;
- real operational inventory rows;
- commands that could delete the operational volume;
- floating-branch production installation;
- public network exposure;
- undocumented critical steps;
- accidental implication that CSV import is recurring synchronization;
- false automatic rollback/downgrade claims.

#187 will physically execute the runbook against the integrated candidate and must surface any operational blocker separately.

## Acceptance mapping

The implementation is complete only when issue #185 acceptance criteria are demonstrably addressed, including:

- one authoritative local operations runbook exists;
- local-first Windows/Docker/WSL 2 topology is preserved;
- prerequisites are explicit;
- every runtime configuration input is named without real secrets;
- production install/update uses an exact release tag;
- operator bootstrap is safe and contains no default committed credential;
- automatic startup and shortcut behavior are documented;
- trusted-LAN URL and host availability limitations are explicit;
- no public exposure/PostgreSQL publication is instructed;
- Flyway fresh-start behavior is explicit;
- CSV import is conditional, one-time, dry-run-first, and never synchronization;
- recurring/pre-upgrade/off-notebook backup behavior references #184;
- start/stop/status/restart/update and smoke checks are concrete;
- recovery references #184 and warns before destructive actions;
- rollback limitations are accurate;
- operator handoff explicitly ends parallel spreadsheet maintenance;
- no production code/schema/dependency/business behavior change is introduced;
- repository documentation checks pass.

## Out of scope

- cloud/provider deployment;
- public DNS/domain/TLS;
- internet-facing remote access;
- infrastructure as code;
- new monitoring platforms;
- PWA/offline behavior;
- product feature tutorials;
- product/backend/frontend changes;
- schema changes;
- automatic self-update;
- automatic database downgrade;
- provider-specific backup SDKs;
- paid infrastructure.
