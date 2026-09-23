# Local operational runtime

This reference defines the repository-owned runtime used by later workstation and go-live work. It is a maintainer procedure, not the final operator runbook.

## Runtime boundary

The supported operator-hosted runtime requires Docker with Docker Compose support. It runs one long-lived service under the stable `lavanda-flow-operational` Compose project:

- `lavanda-flow-app` serves the Angular production application, `/api/v1`, and minimal `/actuator/health` from one HTTP origin;
- PostgreSQL is the managed Supabase database configured through external datasource variables; no operational database volume is created by this project.

The application port is published to the trusted LAN. Stable LAN addressing, firewall rules, Docker engine host startup, and shortcuts belong to #186. Do not expose this HTTP profile to the public internet.

The developer `compose.yaml` remains a separate PostgreSQL convenience workflow with development defaults and a host-published database port. Never use its volume or defaults as the operational database.

## Prepare an exact release

Check out the exact released `vX.Y.Z` revision. Copy the tracked template to the ignored workstation configuration file:

```bash
cp operational.env.example .env.operational
```

Set the managed JDBC URL, username, password, CA certificate path, and deliberately chosen Hikari maximum pool size. Use either the direct IPv6 endpoint or Supavisor session mode on port 5432; never use transaction mode for the persistent JPA runtime. Keep `.env.operational` outside version control and do not pass secrets as Docker build arguments.

Before cutover, test the real operator host and record the evidence: select the direct endpoint when IPv6 works; otherwise select the Supavisor shared session endpoint. The selected pool size must be recorded from the current project limits/usage, leaving headroom for provider-managed connections. This repository pass does not choose a production endpoint or pool value.

The existing operator bootstrap is available through the `LAVANDA_SECURITY_BOOTSTRAP_*` variables. It is disabled by default. When initial provisioning explicitly enables it, supply the username and password externally, then disable bootstrap and remove the plaintext bootstrap password from workstation configuration after the account exists where practical. #185 and #186 own the complete provisioning procedure.

`LAVANDA_HTTP_PORT` defaults to `8080`. `LAVANDA_SESSION_COOKIE_SECURE=false` is the approved trusted-LAN HTTP default; it must become `true` only with a future HTTPS profile. `EXPIRATION_ALERT_WINDOW_DAYS` remains externally configurable.

## Build and start

Build the application image locally from the exact release revision:

```bash
docker compose -f compose.operational.yaml --env-file .env.operational build
```

The image build uses the locked Angular production build, copies its `browser` output into Spring Boot static resources, packages the executable application with Java 25, and leaves Node.js, pnpm, Maven, Angular CLI, source code, and development tooling out of the running image.

Start the runtime once:

```bash
docker compose -f compose.operational.yaml --env-file .env.operational up -d
```

Closing the browser does not stop the application. With the Docker engine running, `restart: unless-stopped` recovers it independently of browser sessions. Actual automatic host startup is configured and tested in #186.

## Status and lifecycle

Inspect service state:

```bash
docker compose -f compose.operational.yaml --env-file .env.operational ps
```

Check the default local health endpoint at `http://localhost:8080/actuator/health`. It exposes aggregate status only. The configured trusted-LAN address uses the same port unless `LAVANDA_HTTP_PORT` changes it.

Restart the application without changing the managed database:

```bash
docker compose -f compose.operational.yaml --env-file .env.operational restart lavanda-flow-app
```

Normal stop and later restart do not modify the managed database:

```bash
docker compose -f compose.operational.yaml --env-file .env.operational down
docker compose -f compose.operational.yaml --env-file .env.operational up -d
```

The operational Compose project has no PostgreSQL volume. The previous local volume, if present, remains untouched and must be retained during the rollback observation window.

Do not use `docker compose ... down -v` against the previous operational runtime during migration. The old local volume is rollback evidence and must not be deleted. Backup and restore are implemented by the managed-target workflow in [PostgreSQL backup and restore](postgresql-backup-restore.md).

## Backup and recovery

Create a successful PostgreSQL logical backup before every application or schema upgrade and follow the recurring backup baseline. Use the guarded disposable restore-verification harness before relying on a backup for recovery. The full maintainer procedure, destructive-recovery warning, off-notebook copy requirement, and retention baseline are in [PostgreSQL backup and restore](postgresql-backup-restore.md).
