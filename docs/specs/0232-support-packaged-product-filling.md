# Issue #232 — Support packaged-product filling

## Status

Approved implementation plan for issue #232.

## Source of truth

Issue #232 is the product source of truth. This specification resolves the implementation details needed to fit the feature into the current production architecture without introducing a parallel filling subsystem.

Read together with:

- `AGENTS.md`;
- `backend/AGENTS.md`;
- `frontend/AGENTS.md`;
- `docs/specs/0235-define-packaged-product-lot-and-genealogy-convention.md`;
- `docs/specs/0153-register-internal-production.md`;
- `docs/product/scope-v1.md`;
- `docs/domain/domain-model.md`;
- `docs/architecture/architecture.md`;
- `docs/architecture/data-model.md`.

## Objective

Implement the smallest complete backend-to-frontend workflow that allows an operator to define and execute filling/bottling production for separately stocked packaged finished-product presentations.

A successful packaged filling execution must:

1. use the existing production aggregate and transaction boundary;
2. consume exact bulk and packaging/component source batches;
3. create exactly one packaged output batch;
4. preserve explicit genealogy to every actual source batch;
5. allocate the generated packaged lot `SSS-MM-YYYY` when generated mode is selected;
6. keep normal internal generated lots on `TTT-EEE-LLL-MM-YYYY` unchanged;
7. expose backend-calculated scaled requirements to Angular without moving scaling logic to the frontend.

## Existing capabilities to reuse

The current implementation already provides the majority of the execution behavior required by this issue:

- one `ProductionFormula` with one output item and ingredient requirements;
- each formula ingredient stores its own `UnitOfMeasure`;
- exact `BigDecimal` scaling in production execution;
- explicit one-or-many source batch allocations per ingredient item;
- inventory-owned batch eligibility, expiration, balance mutation, and movement history;
- one production execution -> one output batch;
- atomic production/inventory transaction;
- explicit production consumptions and recursive genealogy;
- `GENERATED` and `MANUAL` output lot modes;
- Angular production registration with review, exact source-batch selection, authoritative POST result, and backend inventory refresh.

Do not duplicate these capabilities under a new filling module, endpoint family, stock model, or frontend feature root.

## Structural decision: formula kind

The backend needs an explicit persisted discriminator to choose the generated lot convention. Do not infer filling from:

- `UNIT` alone;
- catalog display names;
- the presence of an essence reference;
- source lot-code strings;
- Angular route or UI state.

Add a production-owned formula kind:

```text
ProductionFormulaKind
- STANDARD
- PACKAGED_FILLING
```

Semantics:

- `STANDARD` preserves every existing formula and generated-lot behavior;
- `PACKAGED_FILLING` identifies a formula whose output is a separately stocked packaged finished-product presentation and whose generated lot follows #235.

The formula kind belongs to `production`, not `catalog`, because it describes the production process/definition rather than catalog identity.

Existing persisted formulas are migrated as `STANDARD`.

### HTTP compatibility

Add `kind` to formula create/update/read contracts.

For backward compatibility, create/update requests that omit `kind` are interpreted as `STANDARD`. Responses always expose the canonical wire value.

Do not change the production execution request to accept a lot convention. The selected formula determines the generated convention, preventing the client from choosing an incompatible generated lot policy per execution.

## Packaged-filling formula invariants

For `PACKAGED_FILLING`, backend validation must require:

- output catalog category `FINISHED_PRODUCT`;
- output unit `UNIT`;
- at least one ingredient catalog item with category `FINISHED_PRODUCT` and a non-`UNIT` persisted unit, representing formulated bulk stock;
- all existing active-item and unit-consistency rules;
- at least one ingredient overall, as already required.

Other ingredients may be inventory-controlled bottles, valves, caps, labels, packaging, or other legitimate components. Do not hard-code a mandatory list of packaging categories because Céu de Lavanda may not track every packaging component for every presentation.

Do not require matching fragrance references between output and bulk ingredient in this issue. The formula explicitly identifies the source item, while #229 keeps reference metadata available for catalog identity and operator context. A stronger cross-item fragrance invariant requires a separate approved rule.

`STANDARD` formulas retain current validation and behavior unchanged.

## Catalog public contract delta

The production module currently receives immutable public catalog metadata through `ProductionItemReference` but does not receive category.

Extend that public contract additively with:

```text
Category category
```

This is required so production can enforce `PACKAGED_FILLING` formula invariants without importing catalog internals.

Do not expose catalog JPA entities or repositories.

## Formula persistence

Add one Flyway migration after V15.

Expected migration responsibility:

```text
V16__support_packaged_product_filling.sql
```

### `production_formula`

Add:

```text
formula_kind VARCHAR(...)
```

Requirements:

- existing rows become `STANDARD`;
- final persisted value is non-null;
- allowed values are exactly `STANDARD` and `PACKAGED_FILLING`;
- production code writes the explicit canonical value.

Do not modify historical migrations.

## Generated packaged-lot sequence

Do not overload or reinterpret the existing `production_lot_sequence` table, whose key and semantics are explicitly tied to the normal `TTT-EEE-LLL-MM-YYYY` prefix.

Create a separate production-owned sequence table for generated packaged outputs:

```text
packaged_production_lot_sequence
- production_year
- production_month
- last_sequence
```

Required invariants:

- primary identity is `(production_year, production_month)`;
- `production_month` is `1..12`;
- `last_sequence` is `1..999`;
- one committed allocation advances the sequence atomically;
- concurrent packaged formulas in the same month/year share the same sequence scope;
- month/year changes start a separate sequence scope;
- allocation participates in the enclosing production transaction.

The generated packaged sequence is based on the execution `productionDate`, matching the existing production-date semantics for generated lots.

### Domain/application contracts

Introduce the smallest concrete contracts required for the packaged allocator, for example:

```text
PackagedProductionLotSequenceAllocator
AllocatePackagedProductionLotCode
PackagedProductionLotCode
```

Do not replace the existing normal allocator with a speculative generic strategy hierarchy unless implementation proves a tiny shared helper is necessary.

`AllocatePackagedProductionLotCode` must use `Propagation.MANDATORY`, exactly like current generated allocation, so a later failure rolls sequence allocation back with the complete production transaction.

Generated format:

```text
%03d-%02d-%04d
```

Example:

```text
017-09-2026
```

Sequence exhaustion after `999` must fail with a stable production-domain error and no partial effects.

## Production execution integration

Keep `POST /api/v1/production/executions` and its request shape unchanged.

When `lotCodeMode = GENERATED`:

- `STANDARD` formula -> current `AllocateInternalProductionLotCode` -> `TTT-EEE-LLL-MM-YYYY`;
- `PACKAGED_FILLING` formula -> new packaged allocator -> `SSS-MM-YYYY`.

When `lotCodeMode = MANUAL`:

- preserve the current explicit manual-lot contract for both formula kinds;
- do not parse or infer genealogy from the manual string.

Lot allocation must remain inside the same transaction as inventory effects and execution persistence.

A failure after sequence allocation, including insufficient stock or output persistence failure, must roll back the sequence allocation as part of the same PostgreSQL transaction.

## Mixed-unit formulas

No new unit-conversion model is needed.

The existing formula representation already stores each ingredient quantity in that ingredient item's persisted unit and stores the output quantity in the output item's persisted unit.

A valid filling formula may therefore represent:

```text
output: 1 UNIT packaged perfume

ingredients:
- 30 MILLILITER bulk perfume
- 1 UNIT bottle
- 1 UNIT valve
- 1 UNIT cap
- 1 UNIT label
```

For requested output `5 UNIT`, backend scaling produces:

```text
- 150 MILLILITER bulk perfume
- 5 UNIT bottle
- 5 UNIT valve
- 5 UNIT cap
- 5 UNIT label
```

Do not introduce automatic unit conversion. Scaling is multiplication of each ingredient's own reference quantity by the requested output/reference-output ratio.

## Backend-confirmed requirement preview

Issue #153 intentionally forbade Angular from locally calculating scaled requirements. Issue #232 now requires the operator to see backend-confirmed scaled requirements.

Add a read-only production use case that reuses the exact same scaling logic as execution.

Recommended contract:

```text
GET /api/v1/production/formulas/{formulaId}/requirements?outputQuantity=<decimal>
```

Response contains at minimum:

```text
formulaId
outputInventoryItemId
outputQuantity
outputUnitOfMeasure
requirements[]:
  inventoryItemId
  quantity
  unitOfMeasure
```

Properties:

- no stock is reserved;
- no source batch is selected;
- no sequence is allocated;
- no production execution is created;
- no eligibility claim is made;
- output quantities and requirements remain exact decimal strings at the HTTP/Angular boundary.

### Single scaling rule

Do not maintain separate scaling arithmetic in requirement preview and `RegisterProduction`.

Extract the current exact scaling behavior into one production application/domain collaborator used by both paths, preserving:

- positive quantities;
- max 13 integer digits / 6 fractional digits;
- exact division with no silent rounding;
- `BigDecimal` only.

The production execution remains authoritative and recalculates/revalidates at submission time.

## Inventory and genealogy

No inventory schema or stock model change is required for packaged output.

Continue using the public inventory production contract:

```text
ProductionStockApplication
```

One successful filling execution:

- consumes exact selected source batches;
- records auditable `CONSUMPTION` effects through inventory;
- creates exactly one output `inventory_batch` for the packaged item;
- records one stock-creating output history entry;
- persists `production_consumption` rows linking every actual source batch;
- persists `production_execution.output_batch_id` for the new packaged batch.

Multiple batches of the same bulk ingredient remain supported by the current allocation model. Their summed exact quantity must match the backend-scaled requirement.

Genealogy remains derived exclusively from execution/output/consumption relationships. Never parse either lot convention to discover origin.

## Expiration

Do not add packaged expiration derivation.

Keep the current explicit `outputExpiresAt` request behavior:

- optional under the existing contract;
- if supplied, it cannot precede `outputReceivedAt`;
- filling does not automatically renew, copy, cap, or derive it from bulk source batches.

## Angular formula setup

Extend the existing production formula form rather than creating another setup workflow.

Add `ProductionFormulaKind` to frontend DTOs with exact wire values:

```text
STANDARD
PACKAGED_FILLING
```

Operator-facing labels are pt-BR, for example:

```text
Produção padrão
Envase de produto final
```

For `PACKAGED_FILLING`:

- explain that output stock is a discrete packaged presentation;
- limit output-selection UX to `FINISHED_PRODUCT` items using `UNIT` when practical from existing catalog DTO data;
- show ingredient units from backend/catalog data;
- allow the bulk finished-product item plus packaging/component items;
- do not calculate recipe scaling in the component.

Backend validation remains authoritative even when the UI filters choices for usability.

Existing formula list/detail views should expose the formula-kind label where it improves operator understanding.

## Angular production registration

Reuse the existing `/production/executions/new` workflow.

Do not add a separate filling route unless implementation demonstrates that the current page cannot remain understandable with formula-kind-specific copy. The default plan is one registration page whose selected formula determines context.

### Requirements state

When a formula and valid requested output quantity are available, request backend-confirmed requirements through the new formula-requirements API.

Model explicit states:

```text
idle
loading
ready
error
```

The page displays each backend-confirmed requirement quantity and unit next to the ingredient allocation controls.

Do not:

- calculate the scaled value in TypeScript;
- sum inventory quantities with JavaScript number arithmetic;
- claim stock sufficiency from the preview;
- auto-select FEFO batches in Angular;
- reserve generated lots.

The final production POST continues to validate all allocations authoritatively.

### Lot copy

For a selected `PACKAGED_FILLING` formula with generated mode, operator-facing copy may state that the definitive lot will follow:

```text
SSS-MM-AAAA
```

It must not display a predicted `SSS`.

For a `STANDARD` formula, preserve current generated-lot behavior/copy.

Manual mode remains available through the existing wire value `MANUAL`.

### Success

Continue displaying the backend response as authoritative.

For packaged filling, prominently show the definitive returned lot, for example:

```text
017-09-2026
```

Keep the existing backend inventory refresh behavior. Where an existing catalog/inventory detail route is available, provide or preserve navigation to the output item/batch without depending on #230.

No optimistic balance changes.

## Existing withdrawal flow

No new sales/POS operation is introduced.

Because packaged output is ordinary `FINISHED_PRODUCT` stock in `UNIT`, the existing inventory stock-out/FEFO operation remains the withdrawal path.

Add regression/acceptance coverage proving a packaged output batch can be consumed through the current inventory operation when otherwise eligible. Do not add a parallel packaged-product withdrawal endpoint.

## Error handling

Preserve the shared backend/frontend error architecture.

Add stable errors only where the new domain rule requires them, including:

- invalid packaged-filling formula definition;
- packaged lot sequence exhausted.

Frontend pt-BR localization must cover new stable codes while unknown cases keep the shared generic fallback.

Do not expose SQL constraint names or implementation details.

## Expected backend change surface

Likely production changes include:

- `ProductionFormulaKind` domain enum;
- formula aggregate/persistence/mapper/repository DTO updates;
- formula create/update/read request/response changes;
- `ProductionFormulaDefinitionResolver` packaged validation;
- additive `Category` on public `ProductionItemReference`;
- shared exact requirement calculator;
- read-only scaled-requirements use case and endpoint;
- packaged sequence allocator domain contract + JDBC implementation;
- packaged lot allocation application service;
- `RegisterProduction` generated-lot selection by formula kind;
- V16 migration;
- focused unit, web, integration, rollback, concurrency, Modulith tests.

No new module is expected.

## Expected frontend change surface

Likely frontend changes include:

- formula DTO/API typing for `kind`;
- formula form/list presentation of formula kind;
- requirement-preview DTO/API call;
- production registration requirement state/display;
- packaged generated-lot explanatory copy;
- shared error localization for new backend codes;
- focused Vitest coverage.

Do not add a new state-management dependency.

## Test plan

### Backend unit/application

Cover at minimum:

- existing formulas default/read as `STANDARD` after compatibility mapping;
- creating/updating `STANDARD` formulas preserves existing behavior;
- valid `PACKAGED_FILLING` formula requires `FINISHED_PRODUCT` + `UNIT` output;
- `PACKAGED_FILLING` rejects output with wrong category or unit;
- `PACKAGED_FILLING` requires at least one bulk `FINISHED_PRODUCT` ingredient with non-`UNIT` unit;
- mixed ingredient units are accepted without conversion;
- requirement scaling is exact for multi-unit output;
- unrepresentable scaling is rejected identically by preview and execution;
- standard generated lot remains unchanged;
- packaged generated lot formats `SSS-MM-YYYY`;
- manual lot behavior remains unchanged for both formula kinds.

### PostgreSQL/Testcontainers

Cover at minimum:

- V16 backfills existing formulas as `STANDARD`;
- packaged sequence is global across different packaged formulas in one month/year;
- sequence resets in a different month/year;
- sequence `999` exhaustion;
- concurrent allocations do not duplicate packaged codes;
- failed production after allocation rolls the packaged sequence update back;
- mixed-unit filling consumes exact `BigDecimal` quantities;
- multi-unit filling consumes scaled bulk/component quantities;
- multiple batches of the same bulk ingredient are persisted as separate exact consumptions;
- insufficient/expired/ineligible stock rolls back execution, consumptions, movements, output, and sequence allocation;
- one output batch and output stock history are created;
- upstream/downstream genealogy includes bulk and component batches;
- normal `TTT-EEE-LLL-MM-YYYY` allocation remains unaffected.

### HTTP/OpenAPI

Cover:

- formula `kind` request/response wire values;
- omitted create/update `kind` -> `STANDARD` compatibility behavior;
- requirements endpoint exact decimal response;
- packaged lot returned by normal execution response;
- stable new validation/error codes.

### Frontend

Cover at minimum:

- formula kind selector and exact DTO mapping;
- packaged formula operator copy in pt-BR;
- mixed-unit formula display;
- requirements request triggered from formula + valid output quantity;
- backend requirement quantities displayed without local scaling;
- requirement loading/error state;
- generated packaged mode does not predict/reserve `SSS`;
- success displays definitive backend `SSS-MM-YYYY` lot;
- existing manual mode remains usable;
- multiple source batch allocations remain usable;
- post-success inventory refresh semantics remain unchanged;
- no optimistic stock mutation;
- output stock remains accessible through current inventory/catalog routes.

## Documentation

Update relevant technical documentation after implementation, including:

- API documentation for formula `kind` and requirements endpoint;
- `docs/architecture/data-model.md` implemented physical design for V16;
- any production package/contract documentation that becomes stale;
- this spec if implementation needs a documented correction.

Do not rewrite #235's durable lot contract.

## Implementation order

1. Add V16 and formula-kind persistence/model support with existing rows preserved as `STANDARD`.
2. Extend public catalog metadata with category and enforce packaged-filling formula invariants.
3. Extract/reuse one exact requirement-scaling rule and expose backend requirements preview.
4. Add packaged monthly sequence persistence/allocator and packaged lot-code application service.
5. Integrate formula-kind-aware generated lot selection into `RegisterProduction`; preserve manual and standard paths.
6. Add backend unit/web/Testcontainers/rollback/concurrency/genealogy coverage.
7. Extend Angular formula setup with `STANDARD` / `PACKAGED_FILLING`.
8. Integrate backend-confirmed requirements into existing production registration UI.
9. Add packaged lot copy/result behavior and frontend error localization/tests.
10. Update OpenAPI/technical docs and run full validations.

## Acceptance mapping

The implementation is complete only when all #232 acceptance criteria are satisfied, including:

- separately stocked `FINISHED_PRODUCT` packaged presentation in `UNIT`;
- mixed-unit filling formula;
- exact backend scaling;
- exact multi-batch source allocations;
- one atomic output batch;
- explicit recursive genealogy;
- generated packaged `SSS-MM-YYYY` with global monthly sequence;
- existing standard lot generation unchanged;
- Angular never owns scaling/eligibility/sequence rules;
- existing inventory withdrawal remains usable;
- no packaged-expiration derivation;
- backend and frontend validation suites green.

## Out of scope reinforcement

Do not implement:

- #230 grouped stock workspaces;
- customer/order/POS concepts;
- pricing, costing, fiscal or payment data;
- barcode/QR support;
- automatic bottle choice;
- customer-specific personalization metadata;
- artwork/label printing;
- generic unit conversion;
- automatic packaged expiration derivation;
- production reversal/cancellation;
- a new filling module.

## Final validation

Backend:

```text
cd backend
./mvnw verify
```

Frontend:

```text
cd frontend
pnpm lint
pnpm test
pnpm build
```

Repository review:

```text
git diff --check
git diff
git status --short
```

Before PR creation, review the complete diff for:

- unrelated changes;
- duplicated backend rules in Angular;
- module-boundary violations;
- changes to normal generated-lot semantics;
- accidental expiration derivation;
- floating-point inventory arithmetic;
- missing rollback/concurrency coverage.
