# Issue #231 — Correct initial bulk-perfume snapshot mapping

## Status

Implementation specification. GitHub issue #231 remains the product source of truth; this document fixes the implementation decisions required to execute it safely.

## Source of truth

Apply, in order:

1. GitHub issue #231;
2. `AGENTS.md`;
3. `backend/AGENTS.md`;
4. `docs/specs/0229-model-finished-products-and-shared-fragrance-references.md`;
5. `docs/specs/0136-import-initial-inventory-snapshot.md` and `docs/specs/0168-reconcile-final-snapshot-withdrawals.md` only for importer mechanics that are explicitly preserved here;
6. current `catalog` and `inventory` public contracts and tests on `develop`;
7. this specification for the corrected source contract, grouping, validation, apply behavior, reporting, and documentation delta owned by #231.

When older migration documentation conflicts with this specification, this specification supersedes only the real-cutover source/mapping interpretation. Existing transaction, exact-decimal, expiration, `Retirada`, empty-target, and audit-history invariants remain authoritative unless explicitly changed below.

## Objective

Correct the one-time operational initial inventory import so the revised Céu de Lavanda snapshot creates formulated bulk perfumes as catalog `FINISHED_PRODUCT` items measured in `MILLILITER`, persists the stable fragrance/product metadata introduced by #229, and creates opening batches with the explicit source lot codes.

The importer must continue to be an offline, one-time, inventory-owned migration path. It must not become a generic CSV import feature, a recurring synchronization mechanism, or a history-repair API.

## Baseline after #229

Issue #229 is already part of the `develop` baseline and provides the contracts required by this fix:

- `Category.FINISHED_PRODUCT`;
- `ProductGender` with canonical wire/source codes `M`, `F`, `C`, `M/C`, and `F/C`;
- shared immutable `essenceReference` semantics for canonical essences and derived finished products;
- `InventoryItemRegistration.registerFinishedProduct(FinishedProductRegistration)` as the public catalog write boundary;
- persisted `productionTypeCode`, `essenceReference`, and `gender` metadata;
- PostgreSQL constraints aligned with the finished-product model.

The importer must consume those public contracts. It must not import catalog domain entities, catalog repositories, catalog application internals, or catalog persistence classes.

No new catalog aggregate, inventory aggregate, module, event, dependency, or HTTP endpoint is required.

## Superseded real-cutover interpretation

The following previous assumptions are no longer valid for the corrected real operational snapshot:

```text
category = ESSENCE
lotCode = null
Genero used only to disambiguate duplicate names
```

They are replaced by:

```text
category = FINISHED_PRODUCT
unitOfMeasure = MILLILITER
gender = normalized source Genero
essenceReference = source EssenceReference
productionTypeCode = source ProductionTypeCode
lotCode = source LotCode for every positive opening-stock row
```

The old four-column and five-column source shapes remain historical evidence of #134/#136/#168. They are not accepted by the corrected #231 importer after this change.

## Revised source contract

The importer accepts exactly one UTF-8 CSV header, in exactly this order:

```text
Nome do Perfume,Genero,Ml Disponiveis,Expired,Retirada,EssenceReference,ProductionTypeCode,LotCode
```

Requirements:

- reject reordered columns;
- reject missing columns;
- reject extra columns;
- reject the previous four-column and five-column headers;
- keep the parser source-specific; do not add a generic CSV framework or new dependency;
- preserve deterministic source-row ordering in the report.

The implementation may continue using the current simple comma-delimited parser only while the approved revised source contains no quoting/escaping requirement that the current parser cannot represent. If the actual revised source requires CSV quoting semantics, stop and record that concrete requirement before introducing a dependency or broader parser.

## Field normalization and validation

### `Nome do Perfume`

- trim surrounding whitespace;
- reject blank values;
- preserve the resulting human-readable spelling/case;
- do not fuzzy-correct names;
- do not derive identity from the name;
- do not append gender/reference/type suffixes solely to manufacture uniqueness.

Catalog names are display metadata. The import identity is defined separately below.

### `Genero`

`Genero` is persisted catalog metadata for the imported `FINISHED_PRODUCT`.

Normalize source syntax by:

1. trimming surrounding whitespace;
2. converting letters to uppercase using locale-independent rules;
3. removing whitespace around `/`;
4. parsing the resulting canonical code through the public `ProductGender` contract.

Accepted canonical values are exactly:

```text
M
F
C
M/C
F/C
```

Blank or unsupported values are rejected before APPLY.

The importer owns source-specific normalization. `ProductGender` remains authoritative for canonical code validity.

### `Ml Disponiveis`

Preserve the existing exact-decimal rules:

- trim outer whitespace;
- parse directly to `BigDecimal`;
- reject negative values;
- reject exponent notation and malformed numeric syntax;
- preserve current `StockQuantityRules` precision/scale guarantees;
- never round silently;
- never use `double` or `float`.

### `Retirada`

Preserve the #168 migration-only semantics:

```text
adjustedOpeningQuantity = Ml Disponiveis + Retirada
```

Rules remain:

- blank means zero adjustment;
- accepted nonblank values represent negative milliliter withdrawals using the supported `-50ml` / `- 50ml` style;
- parse the magnitude exactly with `BigDecimal`;
- reject malformed, positive, exponent, unsupported suffix, or excess-precision values;
- reject adjusted quantity below zero;
- create no historical `CONSUMPTION` or adjustment movement from `Retirada`.

All downstream classification and persistence use the adjusted opening quantity.

### `Expired`

Preserve the existing normalization:

- blank is allowed only when existing quantity rules allow it;
- accepted value uses the current `MM/YY` source syntax with existing embedded-whitespace normalization;
- normalize to the last calendar day of that month;
- an adjusted positive quantity requires expiration;
- an adjusted positive row whose expiration precedes the explicit import effective date is rejected;
- adjusted zero may have blank expiration;
- never backdate the effective date automatically.

### `EssenceReference`

- trim surrounding whitespace;
- required for every source row;
- accept exactly `001` through `999`;
- reject `000`;
- reject non-three-digit and otherwise malformed values;
- do not derive or repair a reference from the product name.

The importer should validate the source shape before APPLY, while catalog registration remains authoritative for the persisted domain invariant.

### `ProductionTypeCode`

- trim surrounding whitespace;
- required for every source row;
- accept exactly three uppercase ASCII letters matching the existing catalog contract;
- do not silently uppercase a lowercase source value;
- do not infer a code from product name, gender, or fragrance reference.

The importer validates the source contract and then delegates persistence/domain validation to `catalog`.

### `LotCode`

- trim surrounding whitespace;
- normalized blank becomes `null`;
- adjusted positive quantity requires a nonblank lot code;
- adjusted zero may have a blank lot code because no batch is created;
- adjusted zero may carry a nonblank source lot value, but no batch or lot is persisted for that row;
- reject a nonblank lot code longer than the existing batch limit of 255 characters before APPLY;
- never fabricate placeholder, generated, or synthetic lot codes.

## Source-specific product identity

For this corrected perfume snapshot only, the product identity key is:

```text
productionTypeCode + essenceReference
```

This is an importer grouping rule, not a global catalog uniqueness invariant.

Do not add a PostgreSQL unique constraint on `(productionTypeCode, essenceReference)`: later legitimate finished-product presentations may share those values while remaining distinct catalog items.

### Repeated rows / multiple opening lots

Rows with the same normalized identity key represent the same imported bulk finished product and therefore map to exactly one catalog item.

Within one identity group:

- the normalized display name must be identical across rows;
- normalized `gender` must be identical across rows;
- category and unit are fixed by this importer and therefore identical by construction;
- each adjusted-positive row creates its own opening batch;
- adjusted-positive rows must use distinct normalized nonblank lot codes;
- duplicate lot codes within the same identity group are rejected deterministically;
- source order is preserved for report rows and batch creation order.

When conflicting name or metadata is found for one identity key, reject every affected row in that conflicting group rather than choosing the first row silently.

### Fragrance-level gender consistency

The same `essenceReference` describes the same fragrance lineage even when more than one production type may exist.

Therefore all rows sharing one normalized `essenceReference` must carry the same normalized `gender` value. Conflicting genders for the same fragrance reference are rejected before APPLY.

Gender is not part of the product identity key.

### Equal names across distinct identities

Distinct identity keys may keep the same normalized display name. Do not mutate names merely to make them unique.

The deterministic disambiguation rule is the explicit stable identity metadata:

```text
productionTypeCode + essenceReference
```

The normalized report must expose both fields so equal display names remain reviewable and unambiguous. The importer never resolves distinct products by name equality.

## Validation model

Continue accumulating deterministic row-level failures before business writes where the complete file makes them discoverable.

Retain existing validation codes for unchanged quantity, withdrawal, and expiration behavior. Add the smallest source-specific distinctions required by the revised contract, equivalent to:

```text
INVALID_GENDER
INVALID_ESSENCE_REFERENCE
INVALID_PRODUCTION_TYPE_CODE
LOT_CODE_REQUIRED
INVALID_LOT_CODE
DUPLICATE_LOT_CODE
CONFLICTING_PRODUCT_METADATA
CONFLICTING_FRAGRANCE_GENDER
```

Existing obsolete codes such as the old legacy-reference/name-disambiguation codes may remain in the enum if removing them provides no value, but the revised parser must not use the old `Genero`-as-reference or duplicate-name algorithm.

When multiple errors are discoverable for one row, preserve deterministic first-error behavior unless group-level validation must replace the row result with a conflict that affects the complete identity/fragrance group.

## Deterministic report

Preserve the current top-level report structure and row ordering unless a small additive change is required.

Each normalized row result must expose enough corrected source context to review the future APPLY without consulting hidden parser state. At minimum include:

- source row number;
- normalized catalog name;
- canonical gender code;
- normalized `essenceReference`;
- normalized `productionTypeCode`;
- normalized `lotCode` or `null`;
- adjusted opening quantity;
- normalized expiration or `null`;
- outcome (`CATALOG_ONLY`, `OPENING_STOCK`, or `REJECTED`);
- validation code/reason when rejected.

`catalogOnlyCount` and `openingStockCount` remain source-row outcome counts, not unique catalog-item counts.

DRY_RUN and APPLY must produce identical normalized business results for the same valid source/effective date. APPLY-generated UUIDs are not part of the deterministic report.

## Import plan and APPLY orchestration

The complete file is parsed, normalized, and cross-row validated before the first write.

Required order:

```text
parse exact revised header
-> normalize every row
-> calculate adjusted opening quantities
-> validate row fields
-> validate identity groups / fragrance gender / duplicate lots
-> build deterministic report and grouped apply plan
-> if DRY_RUN: return with zero writes
-> if APPLY: require rejected = 0
-> verify empty catalog guard
-> register each unique finished-product identity once
-> create opening receipts for adjusted-positive rows
-> commit once
```

### Catalog registration

For each unique source identity, call only:

```text
InventoryItemRegistration.registerFinishedProduct(...)
```

with:

```text
name = normalized Nome do Perfume
description = null
unitOfMeasure = MILLILITER
essenceReference = normalized EssenceReference
productionTypeCode = normalized ProductionTypeCode
gender = normalized ProductGender
```

The public catalog registration adapter remains responsible for catalog-domain validation and persistence.

Do not call `registerEssence(...)` for the corrected source.

### Positive opening stock

For every adjusted-positive source row, reuse the existing `RegisterStockReceipt` use case exactly once using the catalog item created/reused for that row's source identity.

Pass:

```text
inventoryItemId = grouped finished-product item id
supplierId = null
lotCode = normalized source LotCode
quantity = adjusted opening quantity
receivedAt = explicit import effective date
expiresAt = normalized source expiration
reason = existing initial-import audit reason
```

The receipt path remains authoritative for creating:

- one batch;
- matching initial/current quantity;
- exactly one immutable `ENTRY` movement.

No production execution, production consumption, supplier, purchase receipt, genealogy edge, historical withdrawal, or reconstructed manufacturing history is created.

### Zero opening stock

For an adjusted-zero row:

- ensure its unique product identity still results in one catalog item if no other row already created that identity;
- create no batch;
- create no movement;
- ignore any nonblank source lot value for persistence because there is no physical opening batch.

## Empty-target guard and atomicity

Preserve existing #136 behavior:

- `DRY_RUN` performs zero business writes;
- APPLY rejects an already initialized catalog before the first write;
- APPLY runs at `SERIALIZABLE` isolation in one outer transaction;
- catalog registration and stock receipts join that transaction;
- do not commit per source row or product group;
- do not introduce `REQUIRES_NEW`;
- a failure during any catalog registration, receipt, batch insert, or movement insert rolls back every prior write from that APPLY.

Do not add a rollback/delete-history endpoint. The operational correction for the mistaken unused import is restoration of the known-good pre-import database backup before running the corrected APPLY.

## Module ownership

- `inventory` owns the initial-snapshot parser, report, apply plan, transaction, batches, quantities, lot codes, and opening movements;
- `catalog` owns finished-product identity metadata and validates finished-product registration through its public API;
- `production` is not changed by this issue;
- `suppliers` is not changed by this issue;
- no internal catalog infrastructure is imported by inventory;
- no business rule moves into `shared`.

Spring Modulith verification must remain green.

## Persistence and schema

No new Flyway migration is expected for #231.

Issue #229 already introduced the required catalog schema/constraints. Existing inventory batch and stock movement schema already support explicit lot codes and exact quantities.

Do not:

- modify historical migrations;
- add a migration-state table;
- add operational CSV data through Flyway;
- add a global uniqueness constraint for the source-specific product identity key;
- add a new lot-code uniqueness constraint without a separate domain requirement.

PostgreSQL remains the source of truth after the accepted corrected APPLY.

## Expected implementation surface

Production code should remain concentrated in the existing initial-snapshot capability and existing public catalog contract usage.

Expected files include, as required by the final diff:

```text
backend/src/main/java/.../inventory/application/initialsnapshot/InitialInventorySnapshotParser.java
backend/src/main/java/.../inventory/application/initialsnapshot/ImportInitialInventorySnapshot.java
backend/src/main/java/.../inventory/application/initialsnapshot/InitialInventoryImportRowResult.java
backend/src/main/java/.../inventory/application/initialsnapshot/InitialInventoryImportValidationCode.java
```

Additional small internal plan/value records inside the same capability are acceptable when they make grouping by source identity explicit and testable. Do not create generic importer abstractions.

Tests expected to change/add include:

```text
backend/src/test/java/.../inventory/application/initialsnapshot/InitialInventorySnapshotParserTest.java
backend/src/test/java/.../inventory/application/initialsnapshot/ImportInitialInventorySnapshotIntegrationTest.java
backend/src/test/java/.../inventory/application/initialsnapshot/ImportInitialInventorySnapshotRollbackIntegrationTest.java
backend/src/test/java/.../acceptance/V1OperationalReadinessAcceptanceTest.java
```

The operational-readiness acceptance test must use synthetic revised eight-column data; it must not keep relying on a source contract that the corrected importer intentionally rejects.

No frontend production-code change is expected for #231.

## Documentation delta

Update active migration documentation so the obsolete operational instructions cannot be used accidentally.

### `docs/operations/initial-inventory-import.md`

Rewrite the active source contract to the revised eight-column header and corrected finished-product mapping.

The current frozen v0.6.0 checksum must not remain presented as the checksum for the corrected source. The revised real CSV receives a new external SHA-256 after operator edits.

Versioned documentation should use a placeholder/instruction for the operator-maintained expected checksum rather than committing corrected real business data.

Keep:

- isolated validation runtime procedure;
- `DRY_RUN` before `APPLY`;
- explicit effective date;
- read-only CSV mount;
- empty-target guard;
- prohibition on `down -v` against the operational runtime;
- post-APPLY PostgreSQL-only operation.

### Historical specs #134, #136, #168

Do not rewrite their historical implementation narrative. Add a concise supersession note where necessary stating that #231 replaces their real-cutover category/header/name-disambiguation assumptions while preserving the mechanics explicitly referenced by this spec.

### `docs/operations/v1-operational-readiness.md`

Preserve the v0.6.0 acceptance evidence as historical evidence, but mark its old five-column CSV/checksum and `ESSENCE`/null-lot conclusions as superseded for the corrected operational cutover.

Do not overwrite the old recorded evidence as though the corrected source had been tested on 2026-09-06.

The corrected real-source DRY_RUN/APPLY evidence belongs to the later v0.6.1 cutover/release procedure (#233), not to synthetic implementation tests in #231.

## Tests

Use only synthetic CSV fixtures/content in versioned tests. Never commit the real corrected CSV, real lot codes, corrected source checksum, credentials, or database dumps.

### Parser / normalization tests

Cover at minimum:

1. exact revised eight-column header is accepted;
2. old four/five-column headers are rejected as superseded;
3. reordered/missing/extra columns are rejected;
4. name trim and blank-name rejection;
5. all five canonical gender values;
6. supported gender whitespace/slash normalization;
7. blank/unsupported gender rejection;
8. `001` and `999` reference acceptance;
9. `000`, malformed, and blank reference rejection;
10. exact uppercase three-letter production type acceptance;
11. lowercase/malformed/blank production type rejection;
12. `Retirada` adjusted-quantity behavior remains exact;
13. positive adjusted stock requires a nonblank lot code;
14. lot-code trim and 255-character boundary;
15. adjusted zero may omit lot code and creates a catalog-only row;
16. existing expiration/effective-date behavior remains intact;
17. same identity plus distinct lot codes is accepted;
18. same identity plus duplicate lot code is rejected;
19. same identity plus conflicting normalized name is rejected;
20. same fragrance reference plus conflicting gender is rejected even across different production type codes;
21. distinct identities with equal display names are accepted and remain distinguishable by report identity fields;
22. deterministic row ordering and validation outcomes.

### PostgreSQL / Testcontainers integration

Prove at minimum:

1. DRY_RUN performs zero catalog, batch, movement, supplier, formula, execution, and consumption writes;
2. APPLY persists `FINISHED_PRODUCT`, `MILLILITER`, gender, essence reference, and production type metadata;
3. one source identity repeated for two positive rows creates one catalog item and two opening batches;
4. each positive batch persists the exact trimmed source lot code;
5. each positive row creates exactly one matching `ENTRY` movement with exact adjusted quantity;
6. adjusted-zero source creates no batch/movement;
7. no `CONSUMPTION`, production execution, production consumption, supplier, or genealogy record is fabricated;
8. invalid/conflicting complete-file input produces zero business writes;
9. initialized-target guard still rejects APPLY before writes;
10. simulated mid-apply failure rolls back all catalog/inventory writes without weakening production code;
11. exact decimal values persist without rounding;
12. current stock/batch/history reads agree with imported persisted state.

### Architecture / regression

- existing Spring Modulith verification passes;
- production lot/reference behavior from #229 remains unchanged;
- no frontend change is required;
- current runtime remains unchanged when initial import is disabled.

## Acceptance checklist

- [ ] Corrected source uses only the exact eight-column contract.
- [ ] Old source shapes are rejected by the corrected importer.
- [ ] Imported catalog items are `FINISHED_PRODUCT` with `MILLILITER`.
- [ ] Gender is required, normalized, and persisted using the five canonical codes.
- [ ] `essenceReference` is required and remains `001`–`999`; `000` is rejected.
- [ ] `productionTypeCode` is required and uses the existing three-uppercase-letter contract.
- [ ] Source-specific identity is `productionTypeCode + essenceReference` only inside this importer.
- [ ] Repeated identity rows create one catalog item and distinct opening batches.
- [ ] Duplicate lots for one identity are rejected.
- [ ] Conflicting metadata for one identity is rejected before APPLY.
- [ ] Conflicting gender for one fragrance reference is rejected before APPLY.
- [ ] Equal names across distinct stable identities do not cause fuzzy merge or synthetic renaming.
- [ ] Positive rows require/persist exact source lot codes.
- [ ] Adjusted-zero rows create no batch/movement.
- [ ] `Retirada`, expiration, exact-decimal, deterministic-report, empty-target, and atomicity guarantees remain intact.
- [ ] Every positive opening batch creates exactly one immutable `ENTRY` movement.
- [ ] No production/supplier/history facts are fabricated.
- [ ] No schema change, new dependency, new module, HTTP endpoint, frontend feature, or recurring importer is introduced.
- [ ] Active operational documentation no longer instructs use of the superseded five-column source/checksum for corrected cutover.
- [ ] Synthetic unit/integration/Testcontainers coverage satisfies the issue scenarios.
- [ ] Spring Modulith verification passes.
- [ ] `./mvnw verify` succeeds.

## Out of scope

- generic or recurring CSV import;
- browser upload/import UI;
- spreadsheet synchronization;
- importing essences, bases, packaging, or arbitrary mixed categories;
- repairing the mistaken imported database in place;
- rewriting or deleting confirmed movement history;
- reconstructing pre-system formulas, production executions, genealogy, suppliers, purchases, or withdrawals;
- grouped stock workspaces (#230);
- filling/bottling workflow (#232);
- catalog maintenance (#222);
- sales, orders, customers, prices, payments, invoices, margins, or analytics;
- new uniqueness semantics for normal catalog registration;
- new Flyway migration unless a previously unknown required invariant is discovered and explicitly reviewed.

## Implementation sequence

1. Re-read issue #231, this spec, root/backend `AGENTS.md`, #229 spec, and the current initial-snapshot implementation/tests.
2. Replace legacy header/reference/name-disambiguation parsing with the exact revised eight-column source contract.
3. Add row-level normalization/validation for gender, stable reference, production type, and lot code while preserving `Retirada`, decimal, and expiration behavior.
4. Add full-file grouping validation for product identity, duplicate lots, metadata conflicts, and fragrance-level gender consistency.
5. Extend the deterministic report/apply plan with normalized identity/lot metadata.
6. Change APPLY to register each unique finished product once through `registerFinishedProduct(...)`, then reuse that ID for its source rows.
7. Pass each positive row's explicit lot code through the existing stock-receipt command; preserve zero-row and audit-history behavior.
8. Update synthetic parser, rollback, integration, and operational-readiness acceptance tests.
9. Update active initial-import documentation and add focused historical supersession notes without rewriting old evidence.
10. Run backend verification and inspect the complete diff for module-boundary, transaction, business-history, and scope regressions.

## Final validation

Run from the repository/backend context as applicable:

```bash
cd backend
./mvnw verify
cd ..
git diff --check
git diff
git status --short
```

Final review must confirm:

- no real operational CSV/checksum/lot data was added;
- no historical Flyway migration was modified;
- no unrelated frontend or production changes were introduced;
- no catalog/internal infrastructure crosses the module boundary;
- no stock/history invariant was weakened to simplify testing;
- all issue #231 acceptance criteria are satisfied or explicitly reported as outstanding.
