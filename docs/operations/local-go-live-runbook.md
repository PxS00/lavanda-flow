# Local go-live runbook

## Purpose and authority

This is the authoritative maintainer-facing runbook for the local Lavanda Flow installation. It sequences installation, provisioning, operation, backup, upgrade, and cutover on the prepared Galaxy Book. It does not replace the detailed contracts for the [runtime](local-operational-runtime.md), [Windows workstation](windows-operator-workstation.md), [backup and recovery](postgresql-backup-restore.md), or [one-time initial inventory import](initial-inventory-import.md).

Normal operators do not use this document to run commands.

## Supported environment

The supported host is the prepared Samsung Galaxy Book running Windows 11 Home, Docker Desktop with the WSL 2 backend, and Docker Compose. WSL 2 is Docker Desktop infrastructure; no operator-facing Ubuntu or other general-purpose WSL distribution is required.

The unchanged `lavanda-flow-operational` project has exactly two long-running services:

- `lavanda-flow-app`, the only published HTTP entry point;
- `postgres`, using the private named `postgres-data` volume.

PostgreSQL port 5432 remains unpublished. The trusted-LAN endpoint is:

```text
http://192.168.15.12:8080
```

The Windows network is Private. The `Lavanda Flow` firewall rule permits inbound TCP 8080 only from `LocalSubnet` on the Private profile, with edge traversal blocked. There is no 5432 rule. Router port forwarding is empty; DMZ, UPnP, and DDNS are disabled. Do not expose this runtime to the public internet.

## Operator daily boundary

The normal operator flow is:

1. Power on the Galaxy Book.
2. Sign in to Windows.
3. Wait for Docker Desktop and the existing runtime to recover automatically.
4. Open the `Lavanda Flow` desktop shortcut.
5. Sign in and use the notebook or tablet on the trusted LAN.

The operator must not need PowerShell, Git Bash, Docker Desktop UI, repository navigation, Docker commands, Git, Maven, pnpm, or IDE tools. The shortcut opens the stable LAN URL above. Browser closure does not stop the application or database.

## Release selection and operational checkout

The maintainer keeps the operational checkout in a controlled Windows directory, for example `%USERPROFILE%\LavandaFlow\app`. Production always runs an immutable `vX.Y.Z` Git tag, never `develop`, an issue branch, a release branch, or another floating reference.

For #187 pre-release validation, record the approved candidate commit from `develop`. It is not production cutover. For #188 production cutover, use `v0.6.0` only after #188 publishes that tag.

From the operational checkout, select and record an exact released version:

```powershell
git fetch --tags origin
git switch --detach vX.Y.Z
git rev-parse HEAD
git status --short
```

For v0.6.0, replace `vX.Y.Z` with `v0.6.0` after the tag exists. Record the resolved commit SHA in the cutover evidence. A non-empty `git status --short` must be investigated before build or production use.

## Required configuration and secrets

For first installation only, create the ignored operational file from the tracked template without overwriting an existing configuration:

```powershell
if (Test-Path .env.operational) {
    throw ".env.operational already exists; do not overwrite operational configuration."
}

Copy-Item operational.env.example .env.operational
```

Upgrades preserve the existing `.env.operational`; never recreate or overwrite it from `operational.env.example` during a normal upgrade. Secrets remain outside source control.

Set every template input outside source control:

| Input | Purpose |
| --- | --- |
| `POSTGRES_DB` | Required PostgreSQL database name. |
| `POSTGRES_USER` | Required PostgreSQL runtime user. |
| `POSTGRES_PASSWORD` | Required PostgreSQL runtime password. |
| `LAVANDA_HTTP_PORT` | Published application port; defaults to `8080`. |
| `LAVANDA_SESSION_COOKIE_SECURE` | Keep `false` only for the approved trusted-LAN HTTP profile. |
| `EXPIRATION_ALERT_WINDOW_DAYS` | Externally configurable expiration-alert window. |
| `LAVANDA_SECURITY_BOOTSTRAP_ENABLED` | Normally `false`; temporarily enable only for first provisioning. |
| `LAVANDA_SECURITY_BOOTSTRAP_USERNAME` | Temporary bootstrap username only. |
| `LAVANDA_SECURITY_BOOTSTRAP_PASSWORD` | Temporary bootstrap password only. |

Never commit `.env.operational`, passwords, or screenshots/logs containing them. Do not pass secrets in Docker build arguments or place them in image layers.

## First installation and Flyway

After selecting the exact tag and completing `.env.operational`, build and start the existing runtime:

```powershell
docker compose -f compose.operational.yaml --env-file .env.operational build
docker compose -f compose.operational.yaml --env-file .env.operational up -d
docker compose -f compose.operational.yaml --env-file .env.operational ps
```

Confirm that only `lavanda-flow-app` and `postgres` are running and that PostgreSQL is not host-published. On the validated host, use this local health check unless the configured port differs:

```powershell
Invoke-WebRequest http://127.0.0.1:8080/actuator/health
```

On a fresh PostgreSQL volume, Flyway creates and applies the tracked schema migrations. PostgreSQL is the data source of truth; Hibernate validates the schema and must not install or evolve it. A Flyway failure is a blocker: do not bypass Flyway, edit historical migrations, or use manual SQL as an installation substitute.

## One-time operator provisioning

Provision the initial operator account only when no account has been provisioned:

1. Choose the username and password outside the repository.
2. Temporarily set `LAVANDA_SECURITY_BOOTSTRAP_ENABLED=true` and the temporary bootstrap username/password in `.env.operational`.
3. Recreate only the application container:

   ```powershell
   docker compose -f compose.operational.yaml --env-file .env.operational up -d --force-recreate lavanda-flow-app
   ```

4. Verify login through the normal Lavanda Flow UI.
5. Set `LAVANDA_SECURITY_BOOTSTRAP_ENABLED=false`.
6. Clear the plaintext bootstrap username/password configuration.
7. Recreate `lavanda-flow-app` with the same command.
8. Verify that the persisted account can still sign in.

Do not retain bootstrap values, document default credentials, or assume retained bootstrap values reset an existing password.

## Conditional one-time initial inventory import

Initial inventory migration is conditional and runs once only. Never run it during a normal restart or upgrade, and never treat the frozen CSV/spreadsheet as synchronization.

Use it only when the approved external CSV is the cutover source and the importer-accepted catalog is uninitialized. An operator account or Flyway history alone does not mean that the catalog is initialized. Follow [Initial inventory snapshot import](initial-inventory-import.md) exactly:

1. Keep the CSV external to the repository; verify its approved checksum and effective date.
2. Stop normal operator use for the migration window.
3. Run `DRY_RUN` first and inspect the deterministic report.
4. Require `rejected=0` before `APPLY`.
5. Verify the source checksum again immediately before `APPLY`.
6. Run `APPLY` only once, against the importer-accepted uninitialized catalog; never bypass its guard.
7. Verify catalog items, batches, current stock, and immutable movement history after apply.

The optional final `Retirada` field has the #136/#168 migration meaning only: it adjusts the opening quantity and never creates reconstructed withdrawal or `CONSUMPTION` history.

The established import documentation uses a non-web Java invocation. A one-off Compose run using the already-built image and a read-only external CSV bind mount has not yet been proven on the prepared Windows host, so this runbook does not invent that command. #187 must validate a safe containerized invocation or explicitly retain the documented host-tooling procedure before cutover.

## Automatic startup, availability, and shortcut

The validated startup chain is:

```text
Windows sign-in
→ Docker Desktop auto-start
→ Docker engine ready
→ lavanda-flow-operational recovers via restart: unless-stopped
→ operator opens Lavanda Flow
```

Closing Docker Desktop's window is harmless; do not quit Docker Desktop during normal operation. Browser closure leaves the runtime available. The PostgreSQL named volume and persisted operator account survived recreation, restart, and full Windows reboot.

Availability is intentionally limited by the host: sleep, shutdown, or Wi-Fi disconnection makes tablet access unavailable. Closing the lid on battery sleeps the host. While plugged in, closing the lid does nothing, keeping the host awake for validated tablet access.

## Normal maintainer lifecycle

Run these only from the operational checkout.

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

### Restart the application only

```powershell
docker compose -f compose.operational.yaml --env-file .env.operational restart lavanda-flow-app
```

For a full non-destructive restart, use the documented `down` command followed by `up -d`.

> Never add `-v` to an operational `docker compose down` command. It deletes the PostgreSQL named volume and its database data.

## Health, logs, and smoke checks

Use only safe diagnostics; do not print `.env.operational` or business rows:

```powershell
docker compose -f compose.operational.yaml --env-file .env.operational ps
docker compose -f compose.operational.yaml --env-file .env.operational logs --tail=200 lavanda-flow-app
docker compose -f compose.operational.yaml --env-file .env.operational logs --tail=200 postgres
Invoke-WebRequest http://127.0.0.1:8080/actuator/health
```

After start, restart, reboot, or upgrade, confirm health, open the stable LAN URL from notebook and tablet, authenticate, navigate non-destructively, and confirm PostgreSQL remains unreachable on TCP 5432. On this host, `localhost:8080` was unreliable; use `127.0.0.1` for host-local health and the stable LAN URL for operator access.

## Backup, recovery, and upgrade

Use [PostgreSQL backup and restore](postgresql-backup-restore.md) as the authoritative recovery contract. Create a successful logical backup after every operating day with changed business data and immediately before every application/schema upgrade. Keep at least seven recent successful daily backups locally, retain the pre-upgrade backup until post-upgrade validation succeeds, and keep weekly copies outside the notebook for four weeks.

Google Drive is the selected off-notebook mechanism. Copy both the `.dump` and `.dump.sha256` files, then verify the copied checksum. Backup operations remain maintainer procedures. The recovery document contains the destructive database-recreation warning; do not replace it with an automated restore or volume-removal shortcut.

For an exact-tag upgrade:

1. Confirm the current runtime is healthy and create/verify the pre-upgrade backup plus off-notebook checksum-verified copy.
2. Fetch tags, detach at the intended `vX.Y.Z` tag, and record its commit SHA.
3. Build from that exact tag and start the existing Compose runtime.
4. Let Flyway validate applied migrations and apply only legitimate forward migrations from the selected release.
5. Run the non-destructive smoke checks above and retain the pre-upgrade backup until they pass.

There is no automatic schema downgrade, zero-downtime rollback, or guarantee that an older application can run against a migrated database. If an upgrade fails after schema migration, stop and follow the destructive recovery contract only after deciding on the exact backup and release revision.

## Operator handoff and cutover

After the approved initial import (if required), smoke checks, backup confirmation, and cutover validation complete:

> Lavanda Flow/PostgreSQL becomes the operational source of truth. The spreadsheet/CSV is frozen as historical migration evidence and is no longer maintained in parallel.

Do not use the CSV as a second operational system or recurring import input.

### Operator handoff checklist

- [ ] The exact released revision and commit SHA are recorded.
- [ ] Notebook login works.
- [ ] Tablet login works on the trusted LAN.
- [ ] The `Lavanda Flow` desktop shortcut works.
- [ ] Automatic startup after Windows sign-in works.
- [ ] The operator understands that tablet access depends on the notebook being awake and connected to Wi-Fi.
- [ ] The operator understands that closed-lid tablet access is available only while the notebook is plugged in.
- [ ] A current local backup exists and its checksum is verified.
- [ ] A current Google Drive off-notebook copy exists and its checksum is verified.
- [ ] The maintainer can identify the [PostgreSQL backup and restore](postgresql-backup-restore.md) recovery contract.
- [ ] Operator credentials were delivered outside the repository.
- [ ] Lavanda Flow/PostgreSQL is declared the operational source of truth.
- [ ] The spreadsheet/CSV is frozen as migration evidence and parallel maintenance has stopped.
