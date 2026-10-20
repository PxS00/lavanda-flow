# Supabase advisor remediation

Related: [issue #282](https://github.com/PxS00/lavanda-flow/issues/282), ADRs 0004 and 0011.

## Evidence and boundaries

Assessment date: 2026-10-20. Project: `lavanda-flow`, PostgreSQL 17, region `sa-east-1`.
Reviewed source: develop commit `afaff868a04169e39d03283fef353aab209092cc`.
Operational Flyway history contains successful V1-V16; reviewed develop includes V20.
Main v0.8.0 already contains V17-V20. The authorized hotfix v0.8.1 adds V21 to that production baseline.
Operational rollout from V16 still requires the existing exact-tag upgrade and backup procedure.

The official changelog and current troubleshooting guidance were reviewed. The recent HTTP TLS cipher
deprecation does not change this JDBC/database configuration procedure.

## Disabled Data API and PostgREST

The Dashboard explicitly showed **Data API is disabled** before execution. Logs showed recurring
`schema "pg_pgrst_no_exposed_schemas" does not exist`, approximately every 32 seconds. The authenticator
role had no `pgrst.db_schemas` override and no provider workaround schema existed.

The [official workaround](https://supabase.com/docs/guides/troubleshooting/schema-pg_pgrst_no_exposed_schemas-does-not-exist)
uses `pgrst_no_exposed_schemas`, without the error name's `pg_` prefix. On 2026-10-20 around 14:01 UTC,
the provider-only script `scripts/operations/configure-empty-postgrest-schema.sql` was executed as one
transaction in the SQL Editor. This does not enable the Data API or change application schema. It was
not registered as an application Flyway migration or a parallel Supabase application migration.

Independent SQL verification at 14:01:11 UTC confirmed:

- schema owner `postgres`, with zero relations, functions and types;
- authenticator configuration `pgrst.db_schemas=pgrst_no_exposed_schemas`;
- original role timeouts/preload configuration retained;
- Flyway history still ends at V16.

The Dashboard still displayed **Data API is disabled** and SQL Editor success after execution. A bounded
log query over 14:01:12-14:04:04 UTC (11:01:12-11:04:04 America/Sao_Paulo) found zero occurrences of
the missing-schema error. This is a short observation, not a guarantee about future provider changes
or delayed log ingestion.

Keep this schema empty. Do not add application objects or grants to it. Provider configuration may be
overwritten by future Dashboard/platform changes; verify again if the log noise returns.

To reverse the role override, only as part of a separately intended configuration change:

```sql
ALTER ROLE authenticator RESET pgrst.db_schemas;
NOTIFY pgrst;
```

Leaving the empty schema in place is harmless. Do not enable Data API as a troubleshooting step.
The current task never authorizes exposing business schemas.

## Four mutable function search paths

The operational advisor reports four functions:

- `prevent_inventory_item_production_reference_mutation()`;
- `prevent_inventory_item_essence_reference_deletion()`;
- `prevent_production_history_mutation()`;
- `prevent_canonical_essence_reclassification()`.

Their deployed bodies match their historical migration definitions and only use trigger records and
PL/pgSQL control flow. None resolves unqualified application objects. Setting `search_path = ''` is
therefore compatible with existing behavior and follows the
[official advisor guidance](https://supabase.com/docs/guides/observability/advisors?queryGroups=lint&lint=0011_function_search_path_mutable).
V21 uses ALTER FUNCTION to preserve existing bodies, signatures, owners, grants and triggers.

V21 is included in the issue #282 production hotfix, **not deployed to the operational database**.
The earlier develop PR #283 is superseded by the hotfix/back-sync flow. The four operational warnings remain until an approved Flyway rollout.
V19/V20 introduce five other sales trigger functions without fixed search paths; they are not among
the four operational findings and remain outside #282. Do not claim this migration clears every
warning after a future commercial upgrade.

## Six unindexed foreign keys

Direct COUNT queries returned 89 inventory batches and zero rows in production formula, formula
ingredient, execution and consumption tables. `pg_stat_user_tables` estimates were zero even for the
89 batches; these estimates are not reliable row counts or proof of inactivity. Available
`pg_stat_statements` contained no matching application workload after filtering metadata/inspection
queries. This does not prove a historical absence of traffic or a performance improvement.

| Referencing FK | Current query evidence | Decision and reassessment trigger |
| --- | --- | --- |
| `inventory_batch.supplier_id` | `SpringDataBatchRepository` filters by item, batch identity, quantity and expiration; supplier is projected, not filtered. Supplier operations update metadata/active state, with no delete endpoint. | Defer at 89 rows. Reassess supplier-filtered batch queries or parent deletion/key-update operations at scale. |
| `production_consumption.source_inventory_item_id` | Consumption detail uses `execution_id`; downstream genealogy uses `source_batch_id`. Existing indexes cover those leading columns. | Defer. Reassess item-based consumption history or measured parent-reference checks. |
| `production_execution.formula_id` | History filters production date and detail uses execution ID. Genealogy traverses output/source batch identities, not formula ID. | Defer. Reassess execution lookup by formula or measured formula parent deletion/key-update checks. |
| `production_execution.output_inventory_item_id` | Output item is a projected snapshot; no current history predicate on it. Output-batch traversal already has a unique index. | Defer. Reassess item-filtered production history or measured catalog parent checks. |
| `production_formula.output_inventory_item_id` | Formulas are read by primary key or listed in ID order (`SpringDataProductionFormulaRepository`). | Defer. Reassess formula lookup by output item or measured catalog parent checks. |
| `production_formula_ingredient.inventory_item_id` | Formula ingredients are loaded for a formula; `(formula_id, position)` and `(formula_id, inventory_item_id)` cover that access direction. | Defer. Reassess reverse ingredient-to-formula queries or measured catalog parent checks. |

All six reported FK parent actions use NO ACTION, not CASCADE. FK enforcement can still scan child
tables on parent deletion/key updates; an index decision must include those operations if introduced
or observed. The formula-to-ingredients CASCADE uses a different FK whose leading column is already
indexed. Do not confuse an indexed join to a parent primary key with a need to index the projected
child FK. No new index migration is justified now.

The seven unused-index notices are informational. Preserve all existing indexes, including movement
history, genealogy, Modulith publication and Flyway indexes. Reassess with representative workload,
growth, known statistics lifetime and query plans; a zero usage counter alone does not justify removal.

Advisor reference: [unindexed foreign keys](https://supabase.com/docs/guides/database/database-linter?lint=0001_unindexed_foreign_keys).

## Validation and rollout status

`mvnw.cmd verify` could not start: JAVA_HOME is unavailable and Java is not installed on the current
Windows/WSL path. Docker Desktop's engine is unavailable and WSL Docker integration is not active.
GitHub Actions executed the initial develop patch and exposed migration-test assertions requiring updates:
four historical migration counts must include V21, and PostgreSQL represents an empty search path as
`search_path=""`. The hotfix corrects those assertions; final CI results are recorded on its PR.
Frontend behavior and contracts remain unchanged; frontend package metadata advances together with the backend to v0.8.1.

The hotfix starts from main commit `8c373e4ba4d0718277bd38900426ef5e5f8cdb44` (v0.8.0).
V1-V20 and every existing index remain unchanged. Changes include V21, its regression test,
necessary migration-count assertions, operational/spec documentation, the provider script and unified patch-version metadata. The source archive has no Git metadata, so local `git status --short` could not be reported.

Before applying V21 in an approved release: validate the full intervening release, take the established
pre-upgrade backup, run backend verification, review changes, and let Flyway apply the migration. Query
the four function settings and rerun security advisors afterwards. Existing trigger exceptions and
history invariants must remain enforced. No runtime deployment or business-data change occurred here.
