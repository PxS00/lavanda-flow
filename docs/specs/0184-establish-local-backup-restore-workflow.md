# Issue #184 — Establish local backup and restore workflow

## Objective

Implement the smallest reliable PostgreSQL backup and restore workflow required before Céu de Lavanda depends on Lavanda Flow as its daily operational source of truth.

The workflow must remain local-first and zero-recurring-cost, use PostgreSQL-native logical backup/restore tooling, keep credentials outside tracked source and logs, preserve Flyway history and application compatibility, and provide a tested disposable restore path that can be executed again during #187 go-live acceptance.

## Source of truth

Apply, in order:

1. GitHub issue #184;
2. `AGENTS.md`;
3. ADR 0010: `docs/architecture/decisions/0010-adopt-local-first-operator-hosted-runtime.md`;
4. `docs/specs/0182-add-local-operational-runtime.md`;
5. `docs/operations/local-operational-runtime.md`;
6. `docs/architecture/data-model.md`;
7. current Flyway migrations under `backend/src/main/resources/db/migration/`;
8. current `compose.operational.yaml`, `.gitignore`, and `.dockerignore`;
9. this specification for the approved implementation details of #184.

Issue #184 remains authoritative for objective, scope, acceptance criteria, constraints, and out-of-scope behavior.

## Branch

```text
chore/184/establish-local-backup-restore-workflow
```

The branch starts from `develop` after #183 was squash-merged.

## Dependency state

#177/ADR 0010 established that PostgreSQL on the operator notebook is the operational source of truth and that recovery is a go-live requirement.

#182 is complete and provides the repository-owned operational runtime:

```text
lavanda-flow-operational
├── lavanda-flow-app
└── postgres
```

#183 is complete and validates that runtime packaging in CI.

#184 is therefore unblocked. #187 will execute restore verification again as part of final go-live acceptance.

## Existing state to preserve

The current operational runtime has these relevant properties:

- PostgreSQL 17 runs as the `postgres` service from `postgres:17-alpine`;
- PostgreSQL is private to the Compose network and has no host-published port;
- operational credentials come from the ignored `.env.operational` file through `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD`;
- operational data lives in the named `postgres-data` volume;
- Flyway is the schema-evolution authority;
- Hibernate uses `ddl-auto: validate` in the operational profile;
- the current schema contains catalog, inventory batch, stock movement, production formula, production execution, production consumption, lot-sequence, operator-account, and Flyway-history data;
- normal `docker compose ... down` preserves the operational volume;
- `docker compose ... down -v` is destructive and is explicitly not a normal operational command.

Do not replace or duplicate that runtime in #184.

## Approved backup model

### PostgreSQL-native logical backup

Use PostgreSQL 17 native logical backup tooling from the existing `postgres` container. The operator/maintainer must not need a host PostgreSQL client installation solely for backup or restore.

The supported backup format is PostgreSQL custom format:

```text
pg_dump --format=custom
```

Use options equivalent to:

```text
--no-owner
--no-privileges
```

The custom format is selected because it is portable across the supported PostgreSQL 17 restore path, can be inspected with `pg_restore --list`, and restores through standard PostgreSQL tooling without coupling Lavanda Flow to another backup format or dependency.

Do not use filesystem copies of the live PostgreSQL data directory as the v0.6.0 backup contract.

Do not add physical replication, WAL archiving, point-in-time recovery, object-storage SDKs, or a second database technology.

### Credentials

Backup and restore commands must consume credentials already present in the operational environment/container. Never embed a real password in:

- source code;
- scripts;
- command examples;
- Docker build arguments;
- GitHub Actions;
- generated logs.

When `PGPASSWORD` is needed inside the PostgreSQL container, derive it from the container's existing `POSTGRES_PASSWORD` environment variable without printing the value.

Do not place a password directly in a command-line argument.

### Backup artifact location

Use this repository-local default directory for maintainer-created backups:

```text
backups/
```

The directory is only a default staging location on the operator notebook. It is not disaster recovery by itself because it shares the notebook failure domain.

Backup artifacts are sensitive operational data and must be excluded from both Git and Docker build contexts.

Update `.gitignore` and `.dockerignore` so the standard backup directory and generated custom-format backup artifacts cannot be committed or baked into the application image.

Do not commit any real Céu de Lavanda database dump.

## Repository scripts

Add only the scripts that materially reduce maintainer error.

Preferred paths:

```text
scripts/operations/backup-postgres.sh
scripts/operations/verify-postgres-restore.sh
```

Use Bash with:

```text
set -euo pipefail
```

Resolve repository paths robustly instead of assuming the caller's current working directory.

Keep the scripts provider-neutral and free of application business logic.

### `backup-postgres.sh`

The backup script must create one consistent custom-format logical dump from the running operational PostgreSQL service.

Default behavior:

1. resolve the repository root;
2. use `compose.operational.yaml`;
3. use `.env.operational` unless an explicit maintainer/test override is provided;
4. fail clearly if the required environment file is unavailable;
5. fail clearly if the target PostgreSQL service is not running/reachable;
6. create the output directory with restrictive permissions where practical;
7. create a UTC timestamped backup name;
8. write first to a temporary/partial file;
9. run `pg_dump` through `docker compose exec -T postgres`;
10. validate the produced dump with `pg_restore --list`;
11. move the completed dump atomically to its final name;
12. create a SHA-256 checksum sidecar for the completed dump;
13. print only the resulting artifact/checksum paths and safe status information.

A preferred filename shape is:

```text
lavanda-flow-YYYYMMDDTHHMMSSZ.dump
lavanda-flow-YYYYMMDDTHHMMSSZ.dump.sha256
```

Use `umask 077` or equivalent before creating backup artifacts.

If backup creation or validation fails, remove the partial file and return non-zero. Never leave a partial file looking like a successful backup.

The script must not automatically delete previous successful backups. Retention deletion is intentionally not automated in #184 because accidental destructive pruning would be worse than the small disk cost of logical backups for this single-operator deployment.

The script may support narrowly scoped overrides useful for disposable validation, such as an alternate environment file, output directory, or Compose project name, but the default operational path must remain simple.

### `verify-postgres-restore.sh`

This script is a restore-verification harness, not a destructive production-restore command.

It must accept a backup artifact path and restore it into a disposable PostgreSQL 17 environment isolated from the real `lavanda-flow-operational` project and volume.

The disposable environment must:

- use PostgreSQL 17;
- use a unique Compose project name that cannot collide with `lavanda-flow-operational`;
- use a separate disposable named volume;
- use generated non-production credentials stored only in a temporary file;
- keep operator bootstrap disabled;
- never reuse the real operational volume;
- never require a host-published PostgreSQL port.

Before restore, validate the backup checksum when the expected sidecar exists and validate the dump structure with `pg_restore --list`.

Restore with options equivalent to:

```text
pg_restore --exit-on-error --no-owner --no-privileges
```

Restore only into the empty disposable database.

Cleanup must run through a shell trap. Destructive cleanup such as `down -v` is allowed only for the disposable restore-verification project. Add an explicit project-name guard before any destructive volume cleanup so the script can never issue `down -v` against `lavanda-flow-operational` by mistake.

## Restore verification contract

The disposable restore verification must prove more than "pg_restore returned zero".

### Schema and Flyway

After restore:

- `flyway_schema_history` must exist;
- no restored Flyway row may report failure;
- current migration history must remain readable and valid;
- starting the current backend against the restored database must use Flyway normally;
- Flyway must validate restored applied migrations and may apply only legitimate migrations present in the current source revision that are newer than the restored backup;
- Hibernate must not create or mutate schema as an installation mechanism.

Do not hard-code a permanent maximum Flyway version in the script. A later legitimate migration must not require editing the restore script merely because its version number increased.

### Representative data and relationships

For the acceptance verification dataset, confirm representative restored data exists for at least:

```text
inventory_item
inventory_batch
stock_movement
production_formula
production_formula_ingredient
production_execution
production_consumption
```

Also verify relational consistency through database joins rather than lot-code parsing or naming conventions.

At minimum validate that:

- restored batches still reference existing catalog items;
- restored movements still reference existing batches;
- restored formula ingredients still reference an existing formula and inventory item;
- every restored production execution still references its formula, output inventory item, and output batch;
- the production execution's output batch preserves the internal lot relationship recorded by the execution;
- every restored production consumption still joins its execution to an existing source batch, source inventory item, and immutable stock movement;
- the genealogy path `source batch -> production consumption -> execution -> output batch` remains queryable and internally consistent.

Database foreign keys already enforce much of this integrity; the verification script should nevertheless execute explicit count/join checks so the restore evidence is visible and repeatable.

Do not print business row contents, operator password hashes, credentials, or sensitive notes. Safe counts and pass/fail evidence are sufficient.

The representative-data requirement is for restore acceptance. Do not commit a real production dump as a fixture. Local implementation validation may create a synthetic disposable dataset at runtime and delete it afterward.

### Backend compatibility

After the restore and relational checks, start the current `lavanda-flow-app` against the disposable restored database using the operational profile.

Use the existing operational Compose definition rather than creating a second application runtime definition.

Use a non-operational local HTTP port for the disposable verification environment, with a narrow override if the default verification port is already occupied.

Wait for the current `/actuator/health` endpoint to report healthy status. This proves that:

- the restored schema is accepted by the current backend;
- Flyway history/checksums are accepted;
- legitimate pending migrations can complete;
- Hibernate validation succeeds;
- the application can start against restored data.

The verification environment must be shut down and its disposable volume deleted after the check, whether the check succeeds or fails.

## Production disaster-restore procedure

Document the production recovery procedure in:

```text
docs/operations/postgresql-backup-restore.md
```

The document must distinguish clearly between:

1. safe disposable restore verification;
2. actual destructive operational recovery.

Do not provide a one-command destructive operational restore script in #184.

The documented operational recovery procedure must require the maintainer to:

1. identify and verify the chosen backup/checksum;
2. record the exact application release/revision associated with the recovery operation when known;
3. stop `lavanda-flow-app` before changing the operational database;
4. keep PostgreSQL available only for the explicit database recreation/restore steps;
5. explicitly recreate an empty target operational database before `pg_restore` rather than restoring over live populated data;
6. restore using the existing operational credentials without logging them;
7. start the application from the intended exact release revision;
8. let Flyway validate/apply only legitimate forward migrations;
9. verify `/actuator/health` and representative operational records before returning the system to normal use.

The documentation must contain a prominent destructive-action warning around database recreation.

Do not instruct normal operators to use `docker compose down -v` as a restore shortcut.

If the primary Docker volume itself is damaged or lost, recreating the database/volume is a maintainer recovery operation. Preserve any recoverable failed volume/data until a verified backup restore succeeds where practical.

## Backup frequency and retention baseline

The v0.6.0 single-operator baseline is:

- create one successful logical backup on every operating day in which business data changed, after the final expected operational changes for that day;
- create an additional backup immediately before every application/schema upgrade, regardless of the daily schedule;
- keep at least the 7 most recent successful daily backups on the notebook while disk capacity remains normal;
- keep one weekly backup for each of the previous 4 weeks outside the notebook failure domain;
- do not prune the pre-upgrade backup until the upgrade and post-upgrade validation have completed successfully;
- after each successful operating-day backup, ensure at least one current verified copy exists outside the notebook's primary storage/failure domain.

This is a recovery baseline, not high-availability or point-in-time recovery. At worst, data entered after the most recent successful backup may need to be reconstructed manually.

Do not automate retention deletion in application code.

## Off-notebook copy

The repository must remain provider-neutral.

At least one current backup copy must leave the operator notebook's primary storage/failure domain through a zero-recurring-cost mechanism chosen operationally, for example:

- an existing cloud-drive folder;
- removable media kept separately;
- another trusted device/storage location.

Copy both:

```text
<backup>.dump
<backup>.dump.sha256
```

Verify the checksum again from the copied location after transfer.

Do not add Google Drive, OneDrive, Dropbox, S3, object-storage, or other provider SDKs to Lavanda Flow for #184.

The exact real off-notebook destination and operator-friendly routine belong in the later workstation/go-live runbook work (#185/#186), while #184 defines the repository backup/recovery contract.

## Security and recovery limitations

Document these limitations explicitly:

- logical dumps contain sensitive business data and operator-account password hashes;
- SHA-256 detects corruption but does not encrypt the backup;
- backup confidentiality depends on OS/device/storage access controls chosen operationally;
- the workflow is not continuous replication and has no point-in-time recovery;
- recovery causes application downtime;
- a backup that was never copied outside the notebook does not protect against notebook/disk loss;
- an untested backup is not considered sufficient go-live recovery evidence;
- restore verification proves recoverability at the tested revision, not permanent future compatibility with arbitrary later schema versions.

Do not introduce custom encryption/key-management infrastructure unless a later concrete requirement requires it.

## Files expected to change

Prefer the smallest coherent set:

```text
.gitignore
.dockerignore
scripts/operations/backup-postgres.sh
scripts/operations/verify-postgres-restore.sh
docs/operations/postgresql-backup-restore.md
docs/operations/local-operational-runtime.md
```

The implementation may adjust this list only when the repository's existing structure requires a clearly better placement.

No backend Java, Angular, API, or Flyway migration change is expected.

If implementation discovers that a schema/application change is required merely to make backup/restore work, stop and report the blocker rather than silently expanding #184.

## Validation

### Static/script checks

Run at minimum:

```text
bash -n scripts/operations/backup-postgres.sh
bash -n scripts/operations/verify-postgres-restore.sh
git diff --check
```

Do not add ShellCheck or another dependency solely for this issue unless already available and useful.

### Real disposable backup/restore test

Using Docker availability, perform an end-to-end local validation with no real Céu de Lavanda production data:

1. create an isolated PostgreSQL 17 source environment;
2. bring its schema to the current Flyway state using the current application/runtime path;
3. create synthetic representative catalog, batch, movement, formula, production execution, internal output lot, production consumption, and genealogy data without committing the generated data/dump;
4. run the repository backup script against that isolated source environment;
5. confirm the dump and checksum are created and readable;
6. run the repository restore-verification script against that dump;
7. confirm restore into a second isolated PostgreSQL 17 database;
8. confirm representative data/relationship checks pass;
9. confirm current backend startup and `/actuator/health` pass against the restored database;
10. confirm restored Flyway history remains valid;
11. remove every disposable container, volume, environment file, dump, and checksum created by the test.

The local validation must never use or delete the real `lavanda-flow-operational` volume.

### Repository checks

Because no product code change is expected, do not run unrelated backend/frontend suites solely as ceremony. The protected-branch PR workflows will still report their normal terminal gates.

If implementation unexpectedly changes backend or frontend code, run the full validation for the affected area and explain why that production-code change was necessary.

Review before completion:

```text
git diff
git diff --check
git status --short
```

## Acceptance criteria mapping

The implementation is complete only when all issue #184 criteria are satisfied:

- PostgreSQL-native logical backup exists and is documented/tested;
- no credentials are committed or logged;
- backup artifacts are excluded from Git and immutable runtime layers;
- a disposable PostgreSQL 17 restore is repeatably executable;
- representative inventory/production/genealogy relationships survive restore;
- the current backend starts successfully against restored data;
- Flyway history remains valid and authoritative;
- pre-upgrade backup is mandatory;
- recurring frequency/retention is documented;
- one current verified copy outside the notebook failure domain is required;
- no provider SDK, paid backup service, replication system, or new database dependency is introduced;
- #187 can execute the same restore verification during final go-live acceptance.

## Out of scope reminders

Do not implement:

- PostgreSQL replication;
- WAL archiving or point-in-time recovery infrastructure;
- cloud backup APIs or provider-specific automation;
- automatic backup upload from application code;
- automatic retention deletion from application code;
- database anonymization/export product features;
- application UI for backup/restore;
- product/domain/API changes;
- historical Flyway migration edits;
- destructive one-command production restore automation.

## Stop conditions

Stop and report before expanding scope if any of these become necessary:

- changing historical Flyway migrations;
- changing application/domain/API behavior for backup compatibility;
- adding a database technology or non-PostgreSQL backup dependency;
- exposing PostgreSQL to the host/LAN;
- using the real operational volume for disposable verification;
- adding a cloud/provider SDK;
- requiring paid infrastructure;
- making backup/restore depend on undocumented host-specific behavior that belongs to #186.
