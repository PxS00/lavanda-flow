# Issue #230 — Expose grouped operational stock workspaces

## Status

Approved implementation specification for GitHub issue #230.

Branch:

`feature/230/expose-grouped-stock-workspaces`

The GitHub issue remains the product source of truth. This document records the implementation decisions required to apply that issue consistently to the current repository.

## Objective

Separate catalog administration from day-to-day stock operation and provide stable, operator-friendly stock workspaces grouped by the physical stock families used by Céu de Lavanda.

The implementation must preserve the single inventory model:

```text
InventoryItem -> Batch -> StockMovement
```

Grouped stock pages are read/navigation views over that model. They do not create new inventory domains, stock tables, aggregates, movement types, or frontend business rules.

## Sources of truth

Before implementation, read:

- GitHub issue #230;
- `AGENTS.md`;
- `backend/AGENTS.md`;
- `frontend/AGENTS.md`;
- `docs/architecture/architecture.md`;
- `docs/architecture/backend-structure.md`;
- `docs/architecture/data-model.md`;
- `docs/domain/domain-model.md`;
- `docs/inventory/fefo-allocation.md`;
- relevant current catalog/inventory/frontend code and tests.

The implementation must preserve all existing contracts not explicitly changed here.

## Current repository assessment

The current repository already provides:

- catalog master-data search under `GET /api/v1/inventory-items`;
- item-level operational overview under `GET /api/v1/inventory/items/{inventoryItemId}/overview`;
- item-level batch inventory;
- minimum-stock configuration;
- movement history;
- FEFO item withdrawal;
- stock receipt registration with `inventoryItemId` query preselection;
- catalog reference metadata;
- stable `FINISHED_PRODUCT` support from #229;
- an Angular item operational page;
- Angular alert, receipt, catalog, supplier, and production workspaces.

The current item overview is intentionally one-item-at-a-time. Using it from a grouped list would create an HTTP N+1 and is not acceptable for #230.

The current catalog HTTP search accepts one optional category. #230 does **not** require changing that catalog HTTP endpoint.

## Architectural ownership

### Catalog

`catalog` owns:

- item identity;
- display name;
- category;
- unit of measure;
- active state;
- stable essence reference;
- stable production type reference.

Catalog remains the owner of category membership and item metadata.

### Inventory

`inventory` owns:

- physical/current stock;
- available stock;
- minimum-stock state;
- low-stock/out-of-stock semantics;
- positive-balance batch counts;
- expiration semantics;
- nearest expiration;
- FEFO withdrawal;
- batches and stock movements.

The grouped operational list is therefore an **inventory-owned read model** that composes public catalog metadata with inventory-owned metrics.

### Frontend

Angular may select one of the approved category sets for a route and send that set to the backend.

Angular must not:

- fetch all items and classify/filter them locally;
- calculate physical/current quantity;
- calculate available quantity;
- calculate FEFO;
- calculate expiration eligibility;
- decide whether a balance is low or out of stock from raw quantities;
- create a second persisted classification model.

## Approved operational groups

The approved fixed groups are:

```text
Finished products
  FINISHED_PRODUCT

Essences
  ESSENCE

Inputs
  BASE
  ALCOHOL
  CHEMICAL_INPUT
  COLORANT
  FIXATIVE

Packaging and components
  BOTTLE
  VALVE
  CAP
  LABEL
  PACKAGING

All stock
  no category restriction
```

`OTHER` must remain visible in `All stock` and catalog administration. It must not be silently assigned to another operational group.

The Angular route configuration may contain these approved category sets solely to construct backend filter requests. Returned rows are never regrouped or filtered by Angular.

## Backend HTTP contract

Add an inventory-owned collection read endpoint:

```http
GET /api/v1/inventory/items
```

This is distinct from the existing catalog master-data endpoint:

```http
GET /api/v1/inventory-items
```

Do not rename or replace either route.

### Query parameters

Supported parameters:

```text
category   optional, repeatable exact catalog category wire value
page       optional, default 0
size       optional, default 20, maximum 100
```

Examples:

```http
GET /api/v1/inventory/items?category=ESSENCE&page=0&size=20

GET /api/v1/inventory/items?category=BASE&category=ALCOHOL&category=CHEMICAL_INPUT&category=COLORANT&category=FIXATIVE&page=0&size=20

GET /api/v1/inventory/items?page=0&size=20
```

Rules:

- no `category` parameter means all catalog categories, including `OTHER`;
- one category must work normally;
- repeated categories use OR semantics;
- duplicate category values must not change results;
- unknown category values return the repository's standard 400 error contract;
- pagination rules remain aligned with existing paginated endpoints;
- results are deterministically ordered by item name and stable identifier, consistent with current catalog search ordering.

Do **not** extend `GET /api/v1/inventory-items` to repeated categories as part of this issue unless implementation proves it is necessary. The preferred solution leaves the catalog HTTP contract unchanged.

## Backend response contract

Return one paginated response with page metadata and backend-authoritative stock summaries.

Each row must expose:

```text
inventoryItemId
name
category
unitOfMeasure
active
essenceReference
productionTypeCode
totalCurrentQuantity
availableQuantity
minimumQuantity
lowStock
outOfStock
nonZeroBatchCount
nearestExpiration
```

The page/read response must also expose the effective stock date context required to interpret date-sensitive metrics:

```text
asOfDate
expirationWindowDays
```

The exact JSON envelope may follow existing page-response conventions, but it must remain typed, documented by OpenAPI, and stable.

### Quantity semantics

Preserve existing item-overview semantics exactly:

- `totalCurrentQuantity` is physical/current stock and may include positive expired balances;
- `availableQuantity` excludes expired and zero-balance stock according to existing inventory rules;
- `expiresAt <= asOfDate` is expired;
- `nearestExpiration` is the earliest future expiration among positive-balance batches;
- `nonZeroBatchCount` counts batches with positive current quantity;
- `outOfStock` follows the existing backend overview semantic;
- `lowStock` follows the existing minimum-stock semantic and active-item behavior;
- exact decimal transport must remain preserved end to end.

Do not introduce alternative list-only stock semantics.

## Catalog public contract

Inventory must not import catalog application, domain, persistence, or JPA internals.

Add the smallest public catalog read contract required for a paginated stock read.

That contract must support, in one module call:

- stable deterministic pagination;
- zero or more exact category wire values;
- return of item id;
- name;
- category wire value;
- unit of measure;
- active state;
- `essenceReference`;
- `productionTypeCode`.

The public contract must use immutable values/records and stable identifiers.

Category values crossing the module boundary must be stable external names. Do not expose `catalog.domain.Category` to inventory merely to implement filtering.

The implementation may adapt existing catalog query/persistence machinery internally, but the existing catalog HTTP behavior must remain compatible.

## Inventory application read model

Add a focused inventory application use case for paginated stock browsing.

The flow is:

```text
validated stock-list query
        ↓
public catalog paginated lookup
        ↓
collect page item IDs
        ↓
one bulk inventory metrics query for the page IDs
        ↓
merge catalog metadata + inventory metrics
        ↓
return rows in catalog page order
```

The list use case must use the application `Clock` and the existing configured expiration alert window so date semantics match the single-item overview.

### No N+1

The implementation must not:

- call `GetInventoryItemOverview` once per row;
- call catalog `findById` once per row;
- call production reference lookup once per row;
- execute one inventory metrics SQL query per row.

One catalog page lookup plus a bounded number of bulk inventory queries for that page is acceptable.

Items with no batches must still appear with zero stock metrics.

Items with minimum-stock configuration but no batches must preserve their minimum/low/out-of-stock semantics.

## Reuse of existing overview semantics

The existing single-item overview and the new grouped-list read model must share the same inventory metrics semantics.

Prefer evolving the existing inventory overview read port/adapter to support bulk metrics and making the one-item overview use the same implementation path, rather than maintaining two independent SQL definitions that can drift.

Do not change the existing `GET /api/v1/inventory/items/{inventoryItemId}/overview` response contract.

## Database impact

No Flyway migration is expected.

The grouped list is a read model over existing:

- `inventory_item`;
- `inventory_batch`;
- `inventory_minimum_stock_level`;
- existing catalog reference metadata.

Do not add denormalized stock-summary tables, materialized summary columns, or separate group tables for #230.

If implementation discovers a real schema requirement, stop and report it rather than silently adding a migration.

## Angular information architecture

### Application navigation

Change the current sidebar information architecture.

The `Estoque` navigation group should contain:

- `Estoque` -> `/inventory`;
- `Entradas` -> existing `/receipts`;
- `Alertas` -> existing `/inventory/alerts`.

The `Cadastros` group should contain:

- `Catálogo` -> existing `/catalog`;
- `Fornecedores` -> existing `/suppliers`.

The current `/catalog` label and accessible name must no longer say `Estoque`.

User-facing text remains pt-BR.

## Angular inventory routes

Keep the existing inventory route namespace.

Approved routes:

```text
/inventory
/inventory/all
/inventory/finished-products
/inventory/essences
/inventory/inputs
/inventory/packaging
/inventory/items/:inventoryItemId
/inventory/alerts
```

`/inventory` should redirect to `/inventory/all`.

Use one reusable grouped stock list page for:

- all;
- finished products;
- essences;
- inputs;
- packaging/components.

Do not copy one large list component per group.

Route data/configuration may provide:

- pt-BR title;
- optional helper text;
- the approved category filter set.

The page sends those categories to the backend. It does not filter the returned page locally.

Existing item and alert routes remain directly reloadable.

## Angular grouped stock list

Add typed inventory stock-list DTOs and a dedicated inventory data-access service.

Do not use the catalog API directly from the stock list.

The list must support:

- loading state;
- empty state;
- error state with retry;
- paginated results;
- direct navigation/reload;
- notebook/tablet-friendly layout;
- keyboard-accessible controls.

Each row must make the following clear:

- item name;
- category;
- active/inactive state;
- physical/current quantity and unit;
- available quantity and unit;
- backend-confirmed stock status;
- nearest expiration when present;
- stable `essenceReference` / `productionTypeCode` when present;
- positive-balance batch count.

The frontend may map backend booleans/wire values to pt-BR presentation labels, but it must not recompute stock rules.

## Row actions

### Open stock

Always provide:

`Abrir estoque`

Target:

```text
/inventory/items/{inventoryItemId}
```

This reuses the existing operational item workspace.

### Register receipt

For an active row, provide:

`Registrar entrada`

Target:

```text
/receipts?inventoryItemId={inventoryItemId}
```

Reuse the existing receipt preselection behavior. Do not add a second receipt API or form.

For inactive rows, do not present an enabled mutation shortcut that the backend will reject. The item remains inspectable.

### Register withdrawal

For an active row, provide:

`Registrar saída`

Target the existing item operational workspace and reuse its current FEFO withdrawal panel.

Do not add a second withdrawal endpoint or client-side batch picker.

The row action may navigate directly to:

```text
/inventory/items/{inventoryItemId}
```

A fragment/query handoff for focus/scroll is optional only if it is small, explicit, reload-safe, and does not create hidden state. It is not required for acceptance.

## Existing item operational page

Preserve all current item operations and contracts.

Update operator-facing withdrawal wording:

Current technical heading:

`Retirada por validade (FEFO)`

Preferred heading:

`Registrar saída`

Keep concise helper copy explaining that eligible batches are selected automatically by FEFO.

Do not alter FEFO behavior, confirmation, quantities, reasons, error handling, or API calls.

The existing item page currently links back to catalog. Adjust navigation copy/linking where appropriate so the operational page naturally returns to the stock workspace without removing access to catalog administration.

## Finished products

The stock list must not merge rows by fragrance/reference.

Every catalog item remains a distinct inventory identity.

Therefore both of these remain distinct and display their persisted unit:

- bulk finished product, typically `MILLILITER`;
- packaged presentation, typically `UNIT`.

Shared `essenceReference` values never imply shared stock identity.

## Backend tests

Add focused coverage for:

### Public catalog contract

- no category restriction;
- one category;
- multiple categories;
- deterministic pagination/order;
- stable reference metadata;
- invalid category handling as applicable;
- existing catalog HTTP single-category behavior remains unchanged.

### Inventory stock-list use case

- catalog page metadata is preserved;
- bulk metrics merged by stable item id;
- page order preserved;
- no-batch item gets zero metrics;
- minimum stock with no available stock;
- physical versus available quantity with expired stock;
- nearest future expiration;
- low-stock and out-of-stock semantics match existing overview;
- inactive item semantics;
- exact quantities;
- application `Clock` used for date-sensitive rules.

### Persistence/read adapter

- metrics are retrieved in bulk for multiple item ids;
- missing batch rows receive correct zero/default metrics;
- no per-item SQL loop is introduced;
- expiration semantics match existing overview.

### HTTP

- all-stock request;
- repeated `category` values;
- single `category` value;
- pagination;
- invalid category;
- response representation;
- authentication;
- no CSRF requirement for GET;
- OpenAPI contract remains accurate.

### Regression

- existing one-item overview remains unchanged;
- Modulith verification passes;
- catalog master-data search remains compatible.

## Frontend tests

Cover:

- application shell shows distinct `Estoque` and `Catálogo` destinations with correct accessible names;
- `/inventory` redirects to `/inventory/all`;
- all stable group routes resolve;
- each route sends the exact approved category set;
- all-stock omits category filters;
- repeated query parameters are encoded correctly;
- loading/empty/error/retry states;
- pagination;
- row identity, unit, quantities, status, expiration, references, and batch count;
- finished-product bulk and packaged-unit rows remain distinct;
- `Abrir estoque` navigation;
- `Registrar entrada` preselection URL;
- `Registrar saída` reaches the existing operational/FEFO flow;
- inactive rows remain inspectable without enabled stock-mutation shortcuts;
- FEFO panel user-facing heading/helper text;
- direct route reload does not require transient in-memory state;
- existing catalog and inventory operational routes continue to work.

## Files and implementation shape

Exact file names may be refined during read-only planning, but the implementation is expected to affect these areas only:

Backend:

- catalog public read contract;
- catalog query adapter/application support for category-set pagination;
- inventory overview/bulk metrics read port;
- inventory persistence metrics query;
- new inventory stock-list application result/query/use case;
- new inventory collection controller/response DTO;
- focused tests.

Frontend:

- application shell navigation;
- inventory routes;
- inventory stock-list DTO/API service;
- one reusable stock-list page;
- existing item operational page / FEFO presentation copy;
- focused tests.

Do not modify production, suppliers, receipt backend semantics, or unrelated catalog CRUD behavior.

## Constraints

- One backend inventory model only.
- No separate stock domain/table/module per group.
- No client-side FEFO, expiration, availability, minimum-stock, low-stock, or batch-eligibility calculation.
- No unbounded catalog load for frontend grouping.
- No N+1 HTTP overview requests.
- No N+1 backend metrics/reference lookup.
- No new write API.
- No new movement type.
- No automatic reorder/purchasing.
- No sales/POS/customer/order/payment/invoice behavior.
- No barcode/QR behavior.
- No formula/production semantic changes.
- No new dependency unless a concrete requirement is demonstrated.
- Preserve existing routes and wire values unrelated to this new read/navigation surface.

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

Review the full diff for:

- accidental frontend stock-rule duplication;
- catalog/inventory module-boundary violations;
- one-query-per-row behavior;
- unrelated catalog CRUD changes;
- duplicated large stock components;
- route conflicts;
- hard-coded date semantics;
- speculative abstractions/dependencies.

## Out of scope

As defined by issue #230, including:

- sales/POS;
- orders;
- customers;
- prices;
- payments;
- invoices;
- revenue reporting;
- new stock movement types;
- automatic purchasing/reordering;
- barcode/QR scanning;
- production/formula semantic changes;
- catalog CRUD expansion from stock pages;
- shelf-life policy #73;
- actionable-alert navigation beyond preserving routes that #224 will consume.

## Relationship to #224

#230 establishes the stable stock routes and operational workspaces.

Issue #224 should then compose its alert actions onto these routes and existing receipt/disposal flows.

Do not implement #224 alert-action behavior inside #230.
