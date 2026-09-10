# PostgreSQL backup and restore

## Purpose and limits

PostgreSQL is Lavanda Flow's operational source of truth. This procedure uses a PostgreSQL 17 custom-format logical dump and the PostgreSQL tools already inside the operational `postgres` container; no host PostgreSQL client installation is required.

Logical dumps contain sensitive business data and operator-account password hashes. Their SHA-256 sidecar detects corruption, but does not encrypt the dump. Protect the notebook and any copy using the OS and storage access controls selected operationally.

This is not replication, continuous backup, or point-in-time recovery. Recovery requires downtime, and data entered after the most recent successful backup might need manual reconstruction. A backup that has not been copied outside the notebook does not protect against notebook or disk loss. An untested backup is not sufficient recovery evidence. Restore verification proves recoverability at the tested application revision, not permanent compatibility with arbitrary future schema versions.

## Create a backup

From an exact application release checkout with the operational PostgreSQL service running:

```bash
scripts/operations/backup-postgres.sh
```

The script reads the ignored `.env.operational`, writes a restricted `backups/lavanda-flow-YYYYMMDDTHHMMSSZ.dump` custom-format dump, validates it with `pg_restore --list`, and writes a matching `.sha256` sidecar. It uses the database container's existing credentials without printing them. The script does not delete older backups.

The optional `--env-file`, `--output-dir`, and `--project-name` arguments are for a maintainer's isolated validation environment. The normal operational command above must keep using the default project and environment file.

Copy both the dump and its checksum sidecar to an existing zero-recurring-cost destination outside the notebook's primary failure domain, such as separate removable media, a trusted device, or an existing cloud-drive folder. After transfer, verify the copied artifact:

```bash
sha256sum -c lavanda-flow-YYYYMMDDTHHMMSSZ.dump.sha256
```

## Verify a backup safely

Run this default disposable verification periodically and after important changes. It is the normal maintainer
path for an operational backup:

```bash
scripts/operations/verify-postgres-restore.sh backups/lavanda-flow-YYYYMMDDTHHMMSSZ.dump
```

The harness verifies the checksum when present, inspects the custom-format dump, restores only into a uniquely
named `lavanda-flow-restore-*` PostgreSQL 17 Compose project and volume, validates the Flyway history, checks
every restored inventory/production/genealogy relationship for broken references, then starts the current
operational application against the restored database and waits for `/actuator/health`. It uses no PostgreSQL
host port. Its trap runs `down -v` only after a project-name guard confirms this is the disposable restore
project; it never targets `lavanda-flow-operational` or its volume.

This generic operational path accepts a valid empty or sparse business database: catalog-only data, inventory
without production, and unused production tables are normal before or during early operation. Zero rows are not
corruption; any broken relationship among rows that do exist fails verification. The harness never inserts
synthetic business rows into the restored backup.

If port `18080` is occupied, use a different disposable port:

```bash
LAVANDA_RESTORE_HTTP_PORT=18081 scripts/operations/verify-postgres-restore.sh backups/lavanda-flow-YYYYMMDDTHHMMSSZ.dump
```

### Strict representative acceptance verification

Use strict representative verification only for #184-style acceptance datasets, after restoring a disposable
backup known to contain representative catalog, inventory, movement, formula, production, consumption, output,
source, and genealogy relationships:

```bash
scripts/operations/verify-postgres-restore.sh --strict-representative backups/lavanda-flow-YYYYMMDDTHHMMSSZ.dump
```

Strict mode performs the same checksum, dump, Flyway, row-integrity, isolated PostgreSQL 17, application-health,
and cleanup checks as the generic path. In addition, it requires positive counts for each representative table
and relationship. It intentionally fails for a valid operational backup that has not yet used one of those
workflows; use the default command for ordinary maintenance verification.

## Destructive operational recovery

> **Warning:** production recovery changes or recreates the operational database. It is a maintainer procedure, not a one-command script. Do not use `docker compose down -v` as a recovery shortcut; preserve any recoverable failed volume/data until a verified backup restore succeeds where practical.

1. Identify the intended dump, verify its SHA-256 checksum, and record the application release/revision associated with the recovery when known.
2. Stop only `lavanda-flow-app` using the existing operational Compose command. Keep PostgreSQL available for the explicit recreation and restore steps.
3. Using the existing operational credentials without printing them, explicitly drop/recreate an empty target operational database. Never restore over a populated live database.
4. Run PostgreSQL `pg_restore --exit-on-error --no-owner --no-privileges` from the existing PostgreSQL 17 container into that empty database.
5. Start the application from the intended exact release revision. Flyway must validate the restored history and may apply only legitimate forward migrations present in that release; Hibernate remains validation-only and must not install the schema.
6. Confirm `/actuator/health` and representative catalog, batch, movement, formula, execution, consumption, and genealogy records before returning to normal operation.

Perform the disposable verification procedure first whenever possible. Do not automate this destructive procedure until a later explicitly approved recovery requirement exists.

## Frequency and retention baseline

- Create one successful backup on every operating day with business-data changes, after the final expected changes.
- Create an additional backup immediately before every application or schema upgrade. Keep that pre-upgrade backup until upgrade and post-upgrade validation complete successfully.
- Keep at least the seven most recent successful daily backups on the notebook while disk capacity is normal.
- Keep one weekly backup for each of the previous four weeks outside the notebook failure domain.
- After each operating-day backup, ensure at least one current verified copy exists outside the notebook's primary storage/failure domain.

Retention deletion is deliberately manual: this workflow does not automatically prune successful backups.
