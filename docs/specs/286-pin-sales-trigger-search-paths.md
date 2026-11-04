# Pin sales trigger function search paths (#286)

## Source of truth and objective

[Issue #286](https://github.com/PxS00/lavanda-flow/issues/286) defines this change.
Pin the five V19/V20 sales trigger functions without changing sales or inventory behavior.
Base: develop at `579f9b87c6839ec8c465ac0689ab31861543ea84`.
Branch: `chore/286/pin-sales-trigger-search-paths`.

## Design and scope

ADRs 0004 and 0011 retain PostgreSQL as source of truth and Flyway as sole application
schema authority. Sales owns confirmed orders, line snapshots, allocations and corrections;
inventory retains stock, FEFO, exact quantities and audit ownership.

V22 is the next unused version. Follow V21 with five `ALTER FUNCTION ... SET search_path = ''`
statements for `prevent_confirmed_sale_mutation()`, `prevent_sale_line_snapshot_mutation()`,
`prevent_sale_allocation_mutation()`, `prevent_referenced_movement_mutation()` and
`prevent_sale_stock_correction_mutation()`. V19/V20 bodies use only OLD/NEW, TG_OP and
PL/pgSQL control flow/RAISE; none resolves an application relation through the search path.
Unqualified signatures deliberately resolve in Flyway's configured application schema,
including alternate-schema tests. No function replacement, table/data rewrite or permission
change is needed. Existing migrations remain byte-for-byte unchanged.

## Acceptance coverage and verification

- Reuse PostgreSQL 17 Testcontainers and the existing Spring test configuration.
- Check clean public-schema migration and clean alternate-schema migration.
- Upgrade an alternate schema from V21 with existing sale/stock/correction history;
  expect exactly V22, successful validation and a no-op retry.
- Compare function identities, signatures, bodies, owners, ACLs and security attributes,
  all function configurations, trigger definitions/bindings/enabled states, indexes and
  business rows before/after upgrade. Exactly the five sales settings may change.
- Verify all five empty paths, including no-argument trigger signatures; public-schema
  settings remain unchanged during alternate-schema upgrades.
- With caller search_path restricted to pg_catalog, reject UPDATE and DELETE for all
  five immutable histories with the original messages. Draft edits/deletes, legacy
  movement edits/deletes and draft-to-confirmed transition still work.
- Keep existing confirmation/correction integration tests to cover transaction, stock,
  FEFO and audit invariants; update only migration-count expectations affected by V22.
- Run `./mvnw verify`, `git diff --check`, review full diff and `git status --short`.
  Report environment blockers without weakening tests or production code.

## Constraints and deferred operational evidence

No Java application/API/UI/dependency/index changes; no commit, push, PR, merge or deploy.
Do not contact or mutate operational Supabase. No provider migrations or Flyway repair.
Forward-only migration; normal release flow and application rollback retain V22.
Operational acceptance remains pending a separately approved deployment: successful
Flyway history, five function settings and trigger states, refreshed Security Advisor,
pre-upgrade backup and exact-tag smoke evidence. Development tests cannot complete it.
