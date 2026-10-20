# Harden database functions and assess Supabase advisor notices

Source of truth: [issue #282](https://github.com/PxS00/lavanda-flow/issues/282).

## Objective and scope

Keep Supabase Data API disabled, apply the official empty-schema PostgREST workaround when applicable,
pin the four currently reported inventory/production trigger functions through Flyway, and assess six
unindexed FKs using actual repository queries and operational evidence. Preserve all existing indexes.

## Implementation

- `V21__pin_inventory_and_production_function_search_paths.sql` changes only the four function settings.
  Signatures resolve through Flyway's configured application schema, including alternate test schemas.
  No function body, owner, privilege, trigger or index is replaced.
- `FunctionSearchPathMigrationTest` upgrades an isolated V20 schema, verifies metadata preservation,
  checks that public function settings are untouched, and exercises catalog immutability with an unrelated
  caller search path. Existing `FlywayMigrationTest` covers all migrations and an older-version upgrade.
- Provider-only configuration and six FK decisions are recorded in
  [the operational assessment](../operations/supabase-advisor-remediation.md).

## Acceptance and validation

- Data API stays disabled before and after the provider workaround.
- The provider schema contains no relations, functions or types; PostgREST points only to that schema.
- V1-V20 remain byte-identical; V21 changes precisely four search paths.
- No new FK indexes are justified by current queries/cardinality; preserve every existing index.
- Run `./mvnw verify` on disposable PostgreSQL/Testcontainers, then review the entire diff and status.
- No frontend behavior changes; frontend validation is not relevant to this SQL-only implementation.

## Constraints and out of scope

Follow AGENTS.md and ADRs 0004/0011. Flyway remains the only application schema authority. Never apply
V21 directly through the provider SQL editor or Supabase migration history. The maintainer explicitly
authorized a production hotfix on 2026-10-20: branch from main, validate, merge through a hotfix PR,
publish v0.8.1, and synchronize the correction into develop. Operational deployment must follow the
exact-tag runbook, including backup and legitimate preceding migrations; do not bypass Flyway or
claim deployment from a GitHub merge. Newer sales trigger functions, new features, Data API enablement
and index removal remain out of scope.

Implementation branch: `hotfix/282/harden-database-functions` from main v0.8.0.
The earlier develop PR #283 is superseded by this hotfix and its ancestry-preserving back-sync.
