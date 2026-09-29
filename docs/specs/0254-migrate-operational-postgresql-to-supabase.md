# Issue #254 — Migrate operational PostgreSQL to Supabase

## Status

Approved for implementation planning.

## Source of truth

- GitHub issue: #254 `chore(database): migrate operational PostgreSQL to Supabase`
- Branch: `chore/254/migrate-postgres-to-supabase`
- Target release: `v0.7.1`
- Related architecture decisions:
  - ADR 0004 — PostgreSQL and Flyway
  - ADR 0010 — local-first operator-hosted runtime
  - ADR 0011 — Supabase managed PostgreSQL for the operational database

The GitHub issue remains authoritative if this specification conflicts with it.

## Objective

Move the operational PostgreSQL database from the operator-notebook Docker runtime to the existing managed Supabase PostgreSQL project while preserving Lavanda Flow's application architecture, security model, database ownership rules, inventory/production invariants, backup guarantees, and local development workflow.

This is an infrastructure and operational migration. It is not a rewrite of Lavanda Flow around Supabase APIs.

## Context

Lavanda Flow v0.7.0 currently runs the Angular production bundle and Spring Boot application from the operator-hosted runtime. The operational Compose topology also owns a local PostgreSQL 17 service and persistent volume.

The repository already separates local development from production database configuration:

- local development uses repository `compose.yaml` and the opt-in `local` Spring profile;
- the base/operational application already accepts standard `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` properties;
- `application-operational.yml` disables Spring Boot Docker Compose integration and keeps Hibernate at `ddl-auto: validate`;
- Flyway is enabled in the base configuration;
- PostgreSQL/Flyway/Testcontainers are established by ADR 0004.

At planning time, the Supabase project is healthy in `sa-east-1` and its `public` schema is empty. No Supabase-managed schema migration history exists there. The repository Flyway chain is V1 through V16.

The current operational backup path is local-container-specific: `backup-postgres.sh` executes `pg_dump` inside the Compose `postgres` service, and the disposable restore harness also reuses the operational Compose PostgreSQL service. Those assumptions must change without weakening backup or restore verification.

## Target architecture

The application boundary remains:

```text
operator browser / trusted-LAN tablet
                |
                | HTTP, existing same-origin contract
                v
      operator-hosted Lavanda Flow app
        Angular + Spring Boot
                |
                | JDBC over TLS
                v
       Supabase managed PostgreSQL
```

The production database moves off the notebook. The Angular application still talks only to Spring Boot. Spring Boot remains responsible for authentication, authorization, business rules, transactions, FEFO, inventory eligibility, production allocation, genealogy, and persistence.

No Supabase application API becomes part of the runtime data path.

## Architectural decisions

### PostgreSQL and schema ownership

- PostgreSQL remains the operational source of truth.
- Flyway remains the only schema-evolution authority.
- Hibernate remains validation-only in the operational profile.
- Existing migrations V1-V16 are immutable.
- No `supabase/migrations` or dashboard-created application tables may compete with Flyway.
- The initial managed schema must be created by the released application/Flyway chain, not manually through Supabase Studio.

### Supabase usage boundary

For #254, Supabase provides managed PostgreSQL infrastructure only.

Explicitly excluded:

- Supabase Auth;
- Supabase Data API as an application data path;
- direct Angular-to-Supabase access;
- `supabase-js` or another Supabase application SDK;
- RLS-based application-user or tenant authorization;
- multitenancy;
- Supabase schema migrations;
- Edge Functions, Realtime, Storage, or SaaS-specific infrastructure.

### Runtime connection mode

Lavanda Flow is a persistent Spring Boot/JPA backend, not a serverless client.

The implementation must select the connection mode from the real operator-host network:

1. Prefer the Supabase direct PostgreSQL connection on port 5432 when the operator host has working IPv6 reachability.
2. If the operator host is IPv4-only and the project has no IPv4 add-on, use the Supavisor shared pooler in **session mode** on port 5432 for application traffic.
3. Do not use Supavisor/PgBouncer transaction mode on port 6543 for the persistent JPA runtime. Transaction mode is intended for short-lived/serverless traffic and has prepared-statement/session-state constraints that are unnecessary here.
4. PostgreSQL-native maintenance operations such as Flyway bootstrap, `pg_dump`, and restore tooling should use a direct connection when it is reachable. If the operator network cannot reach the direct endpoint, the implementation must prove the chosen maintenance path before cutover rather than silently assuming compatibility.

The selected mode must be documented in the operational runbook with the reason it was chosen.

### TLS

All production database traffic must use TLS with server identity/certificate verification where supported by the PostgreSQL JDBC/tooling path. Plaintext database connections are not acceptable for the managed database.

The repository must not embed credentials, passwords, service-role keys, or credential-bearing connection strings.

### Connection pool

Spring Boot/Hikari remains the application-side connection pool.

The implementation must:

- inspect the current Supabase project connection limits;
- choose a deliberately small pool appropriate for one persistent application instance;
- leave headroom for Supabase-managed services and maintenance connections;
- avoid increasing server-side pool size as a substitute for application sizing;
- document the chosen values and validation evidence.

Do not hard-code a pool size based only on generic plan assumptions.

## Scope

### 1. Operational runtime

Adapt the supported operational runtime so the production application connects to managed PostgreSQL rather than requiring the local long-running `postgres` service.

Requirements:

- the production app consumes external datasource properties from the untracked operational environment;
- production startup must not create or depend on a local persistent PostgreSQL volume;
- application HTTP exposure, Spring Security sessions, CSRF, same-origin Angular serving, and trusted-LAN client behavior remain unchanged;
- local development `compose.yaml` remains supported and independent.

The exact Compose shape may retain maintenance-only PostgreSQL tooling if needed, but normal production startup must not start a local operational database.

### 2. Datasource and secrets

Define the minimum operational variables needed for the managed connection without committing real values.

At minimum the implementation must support:

- JDBC datasource URL;
- database username;
- database password;
- any required TLS certificate/trust configuration;
- application connection-pool settings when repository defaults are not appropriate.

Templates must contain names/placeholders only.

Database credentials must remain outside tracked source and immutable image layers.

### 3. Flyway bootstrap and schema validation

On the empty managed `public` schema:

1. confirm the target is the intended Supabase project before any destructive or schema-changing action;
2. verify the schema is still in the expected empty/pre-bootstrap state;
3. start the release candidate against the managed datasource so Flyway applies V1-V16;
4. confirm every Flyway row is successful and the latest version is V16;
5. confirm Hibernate validation succeeds;
6. confirm application health is `UP`;
7. confirm no manual or Supabase migration path created application schema objects.

No historical migration may be edited to make Supabase bootstrap pass. A PostgreSQL compatibility problem must be solved additively or raised as a blocker.

### 4. Backup workflow

Adapt the backup workflow from "execute inside the local operational database container" to "connect to the managed PostgreSQL target".

The resulting workflow must preserve:

- PostgreSQL 17-compatible `pg_dump`;
- custom-format dump;
- `--no-owner`;
- `--no-privileges`;
- non-interactive secret handling;
- restricted local artifact permissions;
- `pg_restore --list` structural validation;
- SHA-256 sidecar;
- fail-on-collision behavior;
- external/off-provider copy;
- copy verification before publication;
- retention behavior that only prunes checksum-valid routine pairs;
- protected pre-upgrade backups.

It is acceptable to use a short-lived PostgreSQL 17 tooling container so the Windows host does not require PostgreSQL client installation. It must not become another long-running production service.

Free-plan provider backups are not a replacement for this repository-owned logical backup. The current Supabase production guidance states that downloadable managed database backups are not available on Free Plan projects, so the off-provider `pg_dump` path remains mandatory.

### 5. Restore verification

Preserve disposable restore verification using a locally isolated PostgreSQL 17 target.

The restore verifier must continue to:

- verify checksum when present;
- restore into a uniquely named disposable target only;
- reject destructive cleanup against the operational project;
- validate Flyway history;
- validate inventory, movement, formula, production, consumption, and genealogy relationships;
- start the current application against the restored database;
- require health `UP`;
- delete only disposable resources it created.

Because the production operational Compose topology will no longer own a local persistent database, the implementation may use a dedicated maintenance/restore Compose definition or an equivalent explicitly isolated Docker path. Do not weaken the existing safety guards to reuse production configuration.

### 6. Windows scheduled backup

Adapt the scheduled Windows runner only as required so routine backups continue from the operator workstation.

Preserve:

- current-user Task Scheduler ownership;
- no stored Windows password;
- existing log behavior and retention;
- missed-trigger behavior;
- overlap prevention;
- external destination verification;
- non-zero failure signaling;
- no credential logging.

Remove readiness assumptions that require the production local PostgreSQL container. Replace them with the minimum checks required for Docker/tooling availability and managed PostgreSQL reachability.

### 7. Operational documentation

Update the relevant runbooks so they clearly distinguish:

- local development PostgreSQL;
- managed production PostgreSQL;
- production application container lifecycle;
- datasource secret configuration;
- chosen direct/session-pooler mode;
- TLS requirements;
- managed-database bootstrap;
- backup;
- restore verification;
- credential rotation;
- Supabase outage/paused-project handling;
- rollback.

ADR 0010 remains authoritative for the operator-hosted application, trusted-LAN access, same-origin delivery, Spring Security session model, and workstation lifecycle. ADR 0011 supersedes only its local operational database placement and related backup assumptions.

## Behavior that must not change

- quantities use exact decimal semantics and `BigDecimal`;
- stock never becomes negative;
- stock mutations remain transactional;
- every stock change remains auditable;
- corrections create new movements rather than rewriting history;
- FEFO remains backend-authoritative;
- `expiresAt <= today` remains expired;
- production scaling/allocation and genealogy remain backend-authoritative;
- Spring Security stateful operator authentication remains;
- CSRF behavior and existing HTTP contracts remain;
- Angular never becomes a database client;
- module boundaries remain unchanged;
- PostgreSQL remains source of truth;
- Flyway remains schema authority;
- Testcontainers remains the integration-test PostgreSQL.

## Files/components expected to change

Exact paths are implementation-dependent, but the current repository indicates likely changes in:

- `compose.operational.yaml`;
- `operational.env.example`;
- `backend/src/main/resources/application-operational.yml` if explicit pool/TLS defaults are needed;
- `scripts/operations/backup-postgres.sh`;
- `scripts/operations/run-scheduled-backup.ps1`;
- `scripts/operations/manage-backup-task.ps1` only if task inputs must change;
- `scripts/operations/verify-postgres-restore.sh`;
- restore/maintenance Docker/Compose support if needed;
- `docs/operations/postgresql-backup-restore.md`;
- `docs/operations/local-operational-runtime.md`;
- `docs/operations/local-go-live-runbook.md`;
- `docs/operations/windows-operator-workstation.md` where local-database assumptions remain;
- architecture documentation where the production topology is described.

No frontend production code change is expected unless repository validation reveals a concrete deployment-contract dependency.

No new Flyway migration is expected for this infrastructure-only change.

## Implementation plan

### Phase A — topology and configuration

1. Inspect all operational Compose, datasource, startup, backup, restore, and workstation contracts.
2. Validate IPv6 reachability from the real operator host to the Supabase direct endpoint.
3. Select direct connection or Supavisor session mode according to the rule above.
4. Verify TLS from the real runtime.
5. Inspect current Supabase connection limits and define a conservative Hikari pool.
6. Refactor operational configuration so the app no longer depends on local production PostgreSQL.
7. Preserve local development unchanged.

### Phase B — backup and restore tooling

1. Refactor `backup-postgres.sh` around an external PostgreSQL target.
2. Preserve all checksum/collision/retention guarantees.
3. Adapt Windows readiness checks.
4. Keep restore verification disposable and local.
5. Add/update contract tests for script behavior before touching the real managed database.

### Phase C — managed database bootstrap

1. Create and verify a final local pre-cutover backup of the current operational database state.
2. Verify an off-notebook copy and checksum.
3. Confirm the Supabase target is the intended project and still in the expected pre-bootstrap state.
4. Apply V1-V16 only through Flyway.
5. Verify Flyway history, Hibernate validation, application health, and operator bootstrap/session behavior.
6. Do not run the external initial business-data CSV unless its separate approved cutover prerequisites are satisfied.

### Phase D — operational cutover

1. Stop writes to the old operational database by stopping the current application before switching datasource authority.
2. Reconfigure only the application datasource/secrets to the managed target.
3. Start the application and verify health/authentication.
4. Verify backup creation from the managed target and disposable restore verification.
5. Verify notebook/tablet application access remains unchanged.
6. Declare the managed PostgreSQL database the operational source of truth only after all gates pass.
7. Keep the old local database/volume intact and unused during the rollback observation window; do not run destructive cleanup.

## Data migration rule

At planning time, the business dataset is still uninitialized. #254 must not fabricate business rows merely to call the hosting migration complete.

The pre-cutover local database is still backed up because it may contain Flyway history, operator-account state, or other operational metadata.

If the approved initial inventory import has not yet been performed, the preferred cutover is a **fresh Flyway bootstrap plus explicit operator provisioning** on Supabase, not an unnecessary dump/restore of an empty business dataset.

If real business data exists before the actual cutover, stop implementation and update the migration plan before moving authority. A populated-source migration requires explicit data-copy/reconciliation acceptance criteria and must not be improvised.

## Validation plan

### Repository validation

Backend:

```bash
cd backend
./mvnw verify
```

Frontend:

```bash
cd frontend
pnpm lint
pnpm test
pnpm build
```

Repository:

```bash
git diff --check
git diff develop...HEAD
git status --short
```

Also require:

- Spring Modulith verification;
- existing Testcontainers PostgreSQL integration coverage;
- backup/restore script contract tests;
- no historical Flyway diff;
- no committed credentials;
- no Data API/Auth/RLS dependency;
- no frontend Supabase dependency.

### Managed PostgreSQL validation

Before release acceptance:

- intended project/region confirmed;
- TLS verified;
- selected connection mode documented;
- connection-pool headroom documented;
- Flyway V1-V16 all successful;
- Hibernate schema validation successful;
- `/actuator/health` reports `UP`;
- operator bootstrap/session path works;
- managed backup succeeds;
- checksum succeeds;
- off-provider copy succeeds;
- disposable restore succeeds;
- no unexpected application tables outside Flyway ownership.

Do not insert synthetic production inventory/production history into the real managed operational database solely for smoke testing. Business-workflow regression remains covered by the existing automated PostgreSQL/Testcontainers suite until approved real business data exists.

## Rollback plan

Rollback must be available until the managed cutover is accepted.

Before switching authority:

- retain the old local PostgreSQL volume unchanged;
- create and verify a final logical backup;
- keep a checksum-verified off-notebook copy;
- record the exact application release/revision and datasource configuration used before cutover.

If managed bootstrap or startup fails before writes are accepted:

1. stop the application;
2. restore the previous datasource configuration;
3. restart against the unchanged local operational database;
4. verify health/authentication;
5. investigate without modifying the failed managed target destructively.

If writes have already been accepted by the managed database, do **not** simply switch back to the stale local database. That would create split-brain/data loss. Stop writes, create a managed backup, reconcile the authoritative state, and execute a separately reviewed recovery procedure.

## Security and secrets

- never commit database passwords or credential-bearing URLs;
- do not expose database credentials to Angular;
- do not use Supabase publishable/service-role keys for JDBC access;
- use a database role/credential appropriate for the application and migration requirements;
- keep TLS enabled with certificate verification;
- keep Spring Security as the operator identity boundary;
- preserve CSRF protection;
- keep operational database access inaccessible from the browser;
- redact credentials from logs and command output;
- document credential rotation.

## Risks and mitigations

### Free Plan automatic pausing

Supabase documents that low-activity Free Plan projects may be paused after a low-activity period. This is an accepted availability constraint only if documented for the operator.

Mitigation:

- document how to identify and resume a paused project;
- do not add artificial keepalive traffic merely to evade provider pausing;
- if guaranteed always-on database availability becomes required, treat a paid-plan decision as an operational change, not hidden application behavior.

### Network dependency

The operator-hosted application now requires internet connectivity to reach the database.

Mitigation:

- fail closed when PostgreSQL is unreachable;
- do not queue speculative local writes;
- document provider/network outage behavior;
- preserve external backups and rollback evidence.

### Provider coupling

Using standard PostgreSQL/JDBC/Flyway limits vendor coupling, but operational endpoints, credentials, connection modes, and project lifecycle are provider-specific.

Mitigation:

- keep provider details in infrastructure/configuration and operations documentation;
- do not import Supabase SDKs into domain/application code.

### Connection exhaustion

A default application pool designed for a local database may reserve unnecessary managed connections.

Mitigation:

- inspect actual project limits;
- choose a small pool;
- validate via Supabase connection observability;
- keep maintenance operations out of the long-lived application pool.

### Backup false confidence

Managed-provider snapshots do not replace an independent restore-tested copy, especially on Free Plan.

Mitigation:

- retain repository-owned `pg_dump`, SHA-256, off-provider copy, and disposable restore verification.

## Out of scope

- Supabase Auth;
- application RLS;
- tenant isolation;
- multitenancy/SaaS;
- direct Angular database access;
- Supabase Data API usage;
- Realtime;
- Edge Functions;
- Storage;
- public application hosting;
- public DNS/domain;
- changing Spring Security auth/session semantics;
- feature work in catalog, inventory, suppliers, or production;
- dependency/framework upgrades unrelated to the migration;
- new schema changes unrelated to a concrete PostgreSQL compatibility blocker;
- redesigning the initial inventory import.

## External technical references

The implementation should re-check current provider documentation immediately before cutover because hosted-service behavior may change:

- Supabase — Connect to your database: https://supabase.com/docs/guides/database/connecting-to-postgres
- Supabase — Connection pooling and limits: https://supabase.com/docs/guides/database/connecting-to-postgres/pooling-and-limits
- Supabase — Project pausing: https://supabase.com/docs/guides/platform/free-project-pausing
- Supabase — Production checklist: https://supabase.com/docs/guides/deployment/going-into-prod

## Completion criteria

#254 implementation is ready for PR review when:

- ADR 0011 and operational documentation match the implemented topology;
- production no longer requires local persistent PostgreSQL;
- local development remains unchanged;
- datasource secrets remain external;
- TLS is verified;
- persistent runtime connection mode is appropriate and documented;
- Hikari sizing is evidence-based;
- V1-V16 bootstrap succeeds on the managed database;
- Hibernate validation and application health pass;
- backup/checksum/off-provider copy/restore verification all pass;
- existing security and domain contracts remain unchanged;
- backend/frontend/repository validation is green;
- final diff contains only #254 scope;
- no synthetic business data was introduced into the operational database;
- rollback is documented and still executable;
- known Free Plan availability limitations are documented.
