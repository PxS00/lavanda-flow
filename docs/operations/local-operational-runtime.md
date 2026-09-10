# Local operational runtime

This reference defines the repository-owned runtime used by later workstation and go-live work. It is a maintainer procedure, not the final operator runbook.

## Runtime boundary

The supported local runtime requires Docker with Docker Compose support. It runs exactly two long-lived services under the stable `lavanda-flow-operational` Compose project:

- `lavanda-flow-app` serves the Angular production application, `/api/v1`, and minimal `/actuator/health` from one HTTP origin;
- `postgres` stores operational data in the private `postgres-data` named volume and has no host-published port.

The application port is published to the trusted LAN. Stable LAN addressing, firewall rules, Docker engine host startup, and shortcuts belong to #186. Do not expose this HTTP profile to the public internet.

The developer `compose.yaml` remains a separate PostgreSQL convenience workflow with development defaults and a host-published database port. Never use its volume or defaults as the operational database.

## Prepare an exact release

Check out the exact released `vX.Y.Z` revision. Copy the tracked template to the ignored workstation configuration file:

```bash
cp operational.env.example .env.operational
```

Set nonblank values for `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD`. The operational Compose configuration fails clearly when any is absent. Keep `.env.operational` outside version control and do not pass secrets as Docker build arguments.

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

Closing the browser does not stop either service. With the Docker engine running, `restart: unless-stopped` recovers both containers independently of browser sessions. Actual automatic host startup is configured and tested in #186.

## Status and lifecycle

Inspect service state:

```bash
docker compose -f compose.operational.yaml --env-file .env.operational ps
```

Check the default local health endpoint at `http://localhost:8080/actuator/health`. It exposes aggregate status only. The configured trusted-LAN address uses the same port unless `LAVANDA_HTTP_PORT` changes it.

Restart only the application while keeping PostgreSQL running:

```bash
docker compose -f compose.operational.yaml --env-file .env.operational restart lavanda-flow-app
```

Normal stop and later restart preserve the named database volume:

```bash
docker compose -f compose.operational.yaml --env-file .env.operational down
docker compose -f compose.operational.yaml --env-file .env.operational up -d
```

The `lavanda-flow-operational_postgres-data` volume survives browser closure, container/runtime restart, normal `down`/`up`, image rebuild, and notebook reboot when Docker storage is preserved.

Do not use `docker compose ... down -v` as a normal command. The `-v` option deletes the PostgreSQL volume and its database data. Backup and restore are implemented by #184 and must be completed before go-live or schema upgrades.

## Backup and recovery

Create a successful PostgreSQL logical backup before every application or schema upgrade and follow the recurring backup baseline. Use the guarded disposable restore-verification harness before relying on a backup for recovery. The full maintainer procedure, destructive-recovery warning, off-notebook copy requirement, and retention baseline are in [PostgreSQL backup and restore](postgresql-backup-restore.md).
