# Issue #225 — Expose production execution history

## Status

Approved implementation specification for GitHub issue #225.

Branch:

`feature/225/expose-production-execution-history`

The GitHub issue remains the product source of truth. This document records the concrete backend/frontend design required by the current repository state after issues #224 and #230.

## Objective

Expose completed production executions as a dedicated operational history workspace so an authenticated operator can answer:

- what was produced;
- when it was produced;
- how much was produced;
- which output batch/lot was created;
- which exact source batches and quantities were consumed.

The feature is a read-only operational history slice. It must reuse persisted production relationships and existing batch/genealogy navigation rather than reconstructing history from lot codes, notes, or inventory movements.

## Sources of truth

Before implementation, read:

- GitHub issue #225;
- `AGENTS.md`;
- `backend/AGENTS.md`;
- `frontend/AGENTS.md`;
- `docs/architecture/architecture.md`;
- `docs/architecture/backend-structure.md`;
- `docs/domain/domain-model.md`;
- `docs/specs/0153-register-internal-production.md`;
- `docs/specs/0154-explore-recursive-batch-genealogy.md`;
- `docs/specs/0232-support-packaged-product-filling.md`;
- current production execution, formula, genealogy, inventory batch-detail, and catalog item-detail code.

## Current repository assessment

The current model already persists all authoritative production history required for this issue.

`production_execution` stores:

- execution identity;
- formula identity;
- output inventory item identity;
- output batch identity;
- exact output quantity;
- definitive lot code and lot-code mode;
- production date;
- output received date;
- optional output expiration;
- completion instant.

`production_consumption` stores, in persisted execution order:

- execution identity;
- source batch identity;
- source inventory item identity;
- consumption movement identity;
- exact consumed quantity.

Completed production history is database-immutable.

The production domain already models the execution and exact consumptions.

Existing public cross-module read contracts already provide:

- `catalog.InventoryItemDetailsLookup` for bulk item display metadata;
- `inventory.BatchDetailsLookup` for bulk batch display metadata.

Existing Angular routes already provide:

- formula management;
- production registration;
- batch genealogy.

Issue #224 also makes the inventory operational workspace targetable by:

`/inventory/items/:inventoryItemId?batchId=:batchId#batches`

No historical schema reconstruction or denormalized history table is required.

## Important historical-context decision

Production formulas do not currently persist a formula name.

A formula is editable while its stable identity remains the same. A completed `ProductionExecution` persists `formulaId` and the actual historical output/consumption facts, but it does not persist a snapshot of the complete formula definition or formula kind at execution time.

Therefore issue #225 must not invent or imply a historical `formulaName`, historical formula kind, or historical formula ingredient snapshot.

The history contract exposes:

- the persisted `formulaId`;
- the persisted production execution facts.

The detail UI may link to the existing current formula route, but the action must be described as the current formula definition, not as an immutable historical formula snapshot.

Do not read the current formula and present it as what the formula necessarily looked like when the execution occurred.

## Architecture

Production remains the owner of production history.

Use a dedicated application read port for history rather than putting Spring pagination/reporting concerns into the domain command repository.

Recommended shape:

```text
HTTP
  |
  v
GetProductionExecutionHistory / GetProductionExecutionDetails
  |
  v
ProductionExecutionHistoryQuery
  |
  v
JpaProductionExecutionHistoryQuery
  |
  v
production_execution / production_consumption
```

Application services enrich persisted production identifiers through existing public module APIs:

```text
production history read
  ├──> catalog.InventoryItemDetailsLookup
  └──> inventory.BatchDetailsLookup
```

No direct cross-module JPA joins are allowed.

Do not make `catalog` or `inventory` depend on `production`.

No new module, event, reporting framework, CQRS framework, or generic query abstraction is justified.

## Persistence strategy

Read existing production tables only.

No Flyway migration is expected.

Do not add a new denormalized history table.

Do not duplicate `inventory_item` or `inventory_batch` display fields into production history.

Do not parse lot-code formats to derive:

- output identity;
- fragrance/reference identity;
- genealogy;
- source batches;
- production type.

The existing explicit UUID relationships remain authoritative.

The current operational data volume does not justify a new index solely for this issue. If implementation evidence demonstrates a real query-plan problem, report it before adding schema/index work.

## List endpoint

Extend the existing execution HTTP resource.

Endpoint:

```text
GET /api/v1/production/executions
```

The existing:

```text
POST /api/v1/production/executions
```

must remain unchanged.

### Query parameters

Supported filters:

- `from`: optional inclusive ISO-8601 `LocalDate` lower bound on `productionDate`;
- `to`: optional inclusive ISO-8601 `LocalDate` upper bound on `productionDate`.

Pagination:

- `page`: zero-based, default `0`;
- `size`: default `20`, minimum `1`, maximum `100`.

No arbitrary sort parameter.

No text search.

No formula filter.

No lot-code filter.

No status filter.

### Why date filters only

Issue #225 allows output item and/or date-range filters when they fit cleanly.

Date range is directly supported by immutable persisted execution data and requires no additional selector semantics.

The existing shared inventory-item selector intentionally searches active catalog items only. Reusing it for historical output filtering would make inactive historical outputs unselectable; generalizing that shared selector is outside the minimum scope of #225.

Therefore this issue implements the date-range filter only.

A future issue may add an output-item history filter if a complete historical selector UX is required.

### Date validation

Both bounds are inclusive.

Valid examples:

```text
from=2026-09-01
to=2026-09-30
```

When both are present:

```text
from <= to
```

must hold.

Invalid pagination or date range uses the standard domain/application error pipeline with a stable error code such as:

`INVALID_PRODUCTION_EXECUTION_HISTORY_QUERY`

Do not manually catch the error in the controller.

### Ordering

Ordering is fixed and deterministic:

```text
productionDate DESC,
completedAt DESC,
executionId DESC
```

This presents the most recent production day first while preserving deterministic ordering for executions sharing dates/timestamps.

## List response

Return a framework-neutral page through an HTTP page response:

```text
content
page
size
totalElements
totalPages
```

Each list row exposes:

- `executionId`;
- `formulaId`;
- `outputInventoryItemId`;
- current catalog display `outputItemName`;
- `outputUnitOfMeasure`;
- `outputBatchId`;
- exact `outputQuantity`;
- persisted `lotCode`;
- persisted `lotCodeMode`;
- `productionDate`;
- `completedAt`.

The list must not load production consumptions.

The application service must bulk-resolve output catalog details for the page through `InventoryItemDetailsLookup.findByIds(...)`.

Do not perform one catalog lookup per history row.

If a persisted output item identity cannot be resolved through the public catalog contract, treat that as an integrity failure rather than silently inventing display data.

## Detail endpoint

Endpoint:

```text
GET /api/v1/production/executions/{executionId}
```

The identifier is the stable persisted production execution UUID.

A missing execution returns the standard not-found error contract with stable code:

`PRODUCTION_EXECUTION_NOT_FOUND`

The route must be reload-safe and not depend on prior list state.

## Detail response

The detail response exposes the complete persisted execution context:

- `executionId`;
- `formulaId`;
- `outputInventoryItemId`;
- current catalog display `outputItemName`;
- `outputUnitOfMeasure`;
- `outputBatchId`;
- exact `outputQuantity`;
- `lotCode`;
- `lotCodeMode`;
- `productionDate`;
- `outputReceivedAt`;
- `outputExpiresAt`;
- `completedAt`;
- exact ordered source consumptions.

Each source consumption exposes:

- `sourceBatchId`;
- `sourceInventoryItemId`;
- current catalog display `sourceItemName`;
- `sourceUnitOfMeasure`;
- inventory-owned `sourceLotCode`;
- persisted `movementId`;
- exact persisted `quantity`.

The exact quantity and IDs come from `production_consumption`.

`sourceLotCode` is display context resolved through the inventory public API; it is not parsed or used to infer genealogy.

The detail application service should bulk-resolve:

- output/source item metadata with `InventoryItemDetailsLookup.findByIds(...)`;
- source batch metadata with `BatchDetailsLookup.findByIds(...)`.

No N+1 module calls.

The response does not need to duplicate the full recursive genealogy tree.

## Read-port records

Keep read-port records framework-neutral.

A practical design is:

- summary record for page entries;
- detail record for one execution;
- persisted-consumption record;
- page record;
- query/filter record.

The persistence adapter may use Spring Data projections/JPQL/native SQL where useful, but Spring Data `Page`/`Pageable` must not leak into the application contract.

List persistence should avoid fetching the `@ElementCollection` consumptions.

Detail persistence may load the consumptions for the single execution.

Preserve persisted consumption ordering.

## Exact decimal transport

Backend quantities remain `BigDecimal`.

HTTP JSON must preserve exact decimal values according to existing project conventions.

Angular DTOs continue representing exact decimal transport as strings where the existing HTTP parsing/client contract does so.

Do not use Java `double`/`float`.

Do not use frontend floating-point arithmetic for production quantities.

Display quantities through the existing exact-decimal formatting helper.

## OpenAPI

Document both GET endpoints in the existing `ProductionExecutionController` or a focused execution-history controller under the same resource, whichever keeps responsibilities clearer without creating an artificial module.

Required OpenAPI behavior:

- list endpoint summary/description;
- filter semantics;
- pagination semantics;
- deterministic ordering;
- 200 response;
- 400 invalid query response;
- detail endpoint summary/description;
- 200 response;
- 404 response.

Do not change the POST registration request/response contract.

## Technical documentation

Update the relevant production/API documentation to record:

- the new history list contract;
- the stable execution detail contract;
- fixed ordering;
- date-filter semantics;
- the distinction between persisted execution facts and current catalog display metadata;
- reuse of genealogy/inventory views.

Do not write a new ADR: this issue does not change module ownership or architecture.

## Frontend routes

Extend `PRODUCTION_ROUTES`.

Required routes:

```text
/production/executions
/production/executions/:executionId
```

Preserve:

```text
/production/executions/new
```

Route order must keep the literal `executions/new` route before `executions/:executionId`.

The detail route must work on direct browser navigation/reload.

## Production navigation

The application shell already has a `Produção` navigation group.

Within that group expose:

- `Fórmulas` -> `/production/formulas`;
- `Histórico` -> `/production/executions`.

The group heading remains `Produção`.

Do not add speculative production-planning destinations.

The production history page should provide an explicit `Registrar produção` action to the existing:

`/production/executions/new`

The formula page may keep its existing registration action.

## History list page

Create one focused page under the existing production feature.

Suggested operator-facing copy:

- heading: `Histórico de produção`;
- description: concise explanation that completed productions are persisted and auditable.

Required states:

- loading;
- loaded with rows;
- empty;
- error with retry.

Each row/card/table entry must make clear:

- produced item;
- quantity + localized unit;
- production date;
- output lot code;
- formula identity;
- execution identity where useful for support/audit.

Primary row action:

- `Ver execução` -> `/production/executions/{executionId}`.

Additional direct genealogy action from the list is optional; detail is the canonical place for source traceability.

## History filters

Expose date filters in pt-BR:

- `De`;
- `Até`.

Use date inputs and normal Angular form validation/presentation patterns.

Applying a filter:

- sends `from`/`to` to the backend;
- resets page to 0;
- never filters loaded results in the browser.

Clearing filters reloads unfiltered page 0.

The frontend may validate basic form consistency for UX, but backend query validation remains authoritative.

Do not calculate production dates or timezone transformations in Angular: these are ISO local production dates.

## Pagination

Use Angular Material paginator consistently with existing application patterns.

Page requests map directly to backend:

- `page`;
- `size`.

Changing page size resets/uses the paginator event according to the backend page request and reloads from the server.

Do not paginate an already-loaded array locally.

Handle a page becoming empty after data changes similarly to established paginated screens where applicable; avoid speculative complexity if completed production history is immutable and deletion is impossible.

## Execution detail page

Suggested heading:

`Detalhes da produção`

Show:

### Production summary

- output item name;
- exact output quantity + localized unit;
- production date;
- output lot code;
- lot-code mode with pt-BR display label;
- output received date;
- optional output expiration;
- completion timestamp;
- execution ID;
- formula ID.

### Formula navigation

Provide:

`Abrir fórmula atual`

to:

`/production/formulas/{formulaId}`

Use wording/helper text that does not claim the current editable formula is a historical snapshot.

Do not fetch the formula merely to relabel historical execution facts.

### Output batch navigation

Provide:

- `Abrir estoque do lote` ->
  `/inventory/items/{outputInventoryItemId}?batchId={outputBatchId}#batches`;
- `Ver genealogia` ->
  `/production/genealogy/batches/{outputBatchId}`.

### Source allocations

Render every persisted consumption in order.

For each source show:

- source item name;
- exact consumed quantity + localized unit;
- lot code or `Não informado`;
- source batch ID when useful for audit.

Actions:

- `Abrir estoque do lote` ->
  `/inventory/items/{sourceInventoryItemId}?batchId={sourceBatchId}#batches`;
- `Ver genealogia` ->
  `/production/genealogy/batches/{sourceBatchId}`.

Do not render a duplicate genealogy tree inside the execution detail page.

## Frontend data access

Extend or split `ProductionExecutionApiService` pragmatically.

It must support:

- existing registration POST unchanged;
- paginated history GET;
- execution-detail GET.

Keep one typed execution service if it remains cohesive.

Add dedicated DTOs/query interfaces for list/detail rather than overloading the registration request model.

Do not introduce a state library or generic repository layer.

## Frontend metadata semantics

Names in history are current display names resolved by the backend at read time.

Historical identity remains the UUID persisted in the execution.

The UI must not say or imply that a current item name was necessarily the exact display name at production time.

Unit labels use existing pt-BR helpers.

Lot-code mode labels:

- `GENERATED` -> `Gerado pelo sistema`;
- `MANUAL` -> `Informado manualmente`.

Wire enum values remain unchanged.

## Backend tests

Cover at least:

### Query validation

- negative page rejected;
- size below 1 rejected;
- size above 100 rejected;
- `from > to` rejected;
- equal `from == to` accepted.

### Persistence/read adapter

- deterministic ordering by production date, completion instant, execution ID;
- pagination;
- inclusive `from`;
- inclusive `to`;
- combined date range;
- list does not require/reconstruct consumptions;
- detail returns exact persisted output fields;
- detail returns exact persisted source consumptions in stored order;
- exact decimal values preserved.

Use PostgreSQL/Testcontainers where persistence behavior is under test.

### Application service

- list bulk-enriches output items;
- detail bulk-enriches output/source items;
- detail bulk-enriches source batches;
- missing execution -> `PRODUCTION_EXECUTION_NOT_FOUND`;
- integrity mismatch in referenced catalog/batch data is not silently hidden.

### HTTP

- authenticated list 200 contract;
- authenticated detail 200 contract;
- invalid filters/pagination 400 with stable code;
- missing execution 404 with stable code;
- POST production registration remains compatible.

### Architecture

Existing Spring Modulith verification must remain green.

## Frontend tests

### API service

Cover:

- unfiltered page query;
- date-filter query;
- pagination;
- detail URL;
- exact DTO transport;
- existing POST registration unchanged.

### History list

Cover:

- loading;
- rows with exact decimal formatting;
- localized units;
- lot and production date;
- detail links;
- empty state;
- error/retry;
- applying date range sends backend filters and resets page;
- clearing filters;
- pagination triggers server request, not local slicing.

### Detail

Cover:

- loading;
- persisted execution summary;
- exact source quantities;
- source lot display;
- current-formula link wording;
- output inventory target link;
- output genealogy link;
- source inventory target links;
- source genealogy links;
- empty source list is not expected for valid persisted executions, but do not manufacture fallback relationships;
- error/not-found through shared error UI.

### Routes/navigation

Cover:

- `/production/executions/new` still resolves registration;
- `/production/executions` resolves history;
- `/production/executions/:executionId` resolves detail;
- direct detail navigation/reload fetches by route ID;
- literal `new` is never captured as an execution ID;
- shell exposes `Fórmulas` and `Histórico`.

### Operational workflow

Add only focused high-value coverage if it provides evidence beyond page/service tests, for example:

`history -> detail -> existing genealogy route`.

Do not duplicate all component-level assertions.

## Existing behavior that must not change

- production registration validation;
- production transaction atomicity;
- generated/manual lot allocation;
- packaged filling behavior;
- inventory stock mutations;
- FEFO/expiration rules;
- formula editing behavior;
- recursive genealogy behavior;
- production execution immutability;
- exact decimal contracts.

## Expected implementation areas

Backend production:

- new execution-history application query/use cases/records;
- production persistence read adapter/query support;
- execution history/detail HTTP responses;
- GET methods on the existing execution resource or a focused controller under the same URL resource;
- stable history validation/not-found exceptions.

Backend tests:

- application;
- persistence integration;
- HTTP/API.

Frontend production:

- production execution DTO/service additions;
- history list page;
- execution detail page;
- production routes;
- application-shell production navigation.

Frontend tests:

- service;
- list;
- detail;
- routes;
- shell;
- focused workflow only if useful.

Documentation:

- this spec;
- relevant API/production documentation where current contracts are enumerated.

Expected schema changes:

none.

## Out of scope

Preserve issue #225 exclusions and additionally avoid:

- analytics/charts/KPIs;
- costing/margin;
- production planning/scheduling;
- editing/deleting/reversing executions;
- CSV/PDF exports;
- formula versioning;
- formula snapshots;
- new formula-name field;
- new genealogy semantics;
- duplicated genealogy rendering;
- arbitrary history sorting;
- generic text search;
- output-item selector redesign;
- new database history table;
- frontend reconstruction from stock movements;
- lot-code parsing.

## Validation

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
git diff
git status --short
```

Record known non-blocking warnings rather than treating them as new failures.

## Review checkpoints

Before completion, inspect the complete diff for:

- current formula definition presented as historical truth;
- genealogy inferred from lot strings;
- production history reconstructed from inventory movement notes;
- direct cross-module JPA/repository access;
- one catalog/batch lookup per execution or source;
- frontend filtering of loaded history;
- frontend floating-point quantity handling;
- arbitrary client-side sort;
- changes to POST registration semantics;
- schema/migration work without demonstrated need;
- duplicate genealogy UI;
- inactive/history data accidentally hidden by active-only catalog semantics;
- unrelated #226/#227 work.

## Acceptance mapping

- Authenticated operator gets a visible history destination.
- List is backend-paginated and deterministically ordered.
- Rows identify output item, quantity, production date, output lot/batch, execution, and formula identity.
- Detail route is stable and reload-safe.
- Detail exposes persisted execution context and exact source batch consumptions.
- History comes from production relationships, not inference.
- Existing genealogy and inventory batch-target views are linked rather than duplicated.
- Date filters execute on the backend.
- All new history GET endpoints have Angular flows.
- Registration behavior is unchanged.
- Exact decimal quantities remain exact end to end.
- OpenAPI and technical documentation describe the read contract.
- Modulith boundaries remain intact.
- Backend/frontend validation covers list, detail, pagination, filtering, not-found, source allocations, direct navigation, and UI states.
