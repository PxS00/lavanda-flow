# Issue #154 — Explore recursive batch genealogy

## Objective

Implement the V1 Angular operator view for exploring backend-authoritative recursive batch genealogy from any known inventory batch.

The frontend must present the recursive upstream/downstream structure returned by the existing production genealogy API. It must not reconstruct genealogy from stock movements, lot codes, notes, dates, formula data, or other client-side heuristics.

## Source of truth

Apply, in order:

- GitHub issue #154 for product intent, scope, and acceptance criteria;
- `AGENTS.md` and `frontend/AGENTS.md` for repository and Angular rules;
- backend issue #151 and the current backend code/OpenAPI for the genealogy HTTP contract;
- `docs/product/scope-v1.md` for durable V1 behavior;
- ADR 0009 (`docs/architecture/decisions/0009-define-v1-production-module-boundaries.md`) for production/inventory ownership;
- this specification for the implementation-specific frontend delta of #154.

Do not introduce backend changes in this issue.

## Existing backend contract

Use the existing production-owned read endpoint:

```text
GET /api/v1/production/genealogy/batches/{batchId}?direction={direction}
```

`batchId` is the stable inventory batch UUID used as graph identity.

`direction` preserves the backend wire values:

```text
UPSTREAM
DOWNSTREAM
BOTH
```

The backend defaults to `BOTH` when `direction` is omitted. The frontend may send the selected direction explicitly for clarity, but must not invent additional traversal modes.

The successful response is authoritative and currently contains:

- `direction`;
- `rootBatch`;
- `upstream[]`;
- `downstream[]`.

Each genealogy batch contains current display facts:

- `batchId`;
- `origin`;
- `inventoryItemId`;
- `itemName`;
- `itemCategory`;
- `unitOfMeasure`;
- optional `supplierId`;
- optional/display `lotCode`;
- `receivedAt`;
- optional `expiresAt`.

Preserve backend batch-origin wire values exactly. Operator-facing origin labels must be pt-BR.

Each genealogy edge contains:

- `executionId`;
- `formulaId`;
- `productionDate`;
- `completedAt`;
- exact persisted decimal `consumedQuantity`;
- `sourceBatch`;
- `outputBatch`;
- recursive `next[]`.

`next[]` is already the backend-authoritative recursive continuation. Angular may recursively render this returned structure, but must not perform graph discovery, infer missing edges, deduplicate relationships using display fields, or derive adjacency from other APIs.

Missing batches preserve the stable backend error code:

```text
BATCH_NOT_FOUND
```

Reuse the shared frontend HTTP error model/localization rather than introducing a genealogy-specific error taxonomy.

## Feature placement and routing

Keep genealogy inside the existing production feature:

```text
frontend/src/app/features/production/
├── data-access/
├── pages/
└── production.routes.ts
```

Add one lazy route:

```text
/production/genealogy/batches/:batchId
```

The route uses stable `batchId` identity only. Do not use lot code or item name as a route key.

### Discoverability

The existing inventory operational page already renders concrete batches for an inventory item. Add a small `Rastrear genealogia` action for each batch row that navigates to the production genealogy route using that row's `batchId`.

Do not create a second batch-search/catalog workflow solely for #154 and do not restructure global navigation.

## Data-access boundary

Add the smallest typed genealogy HTTP client required by #154 under `features/production/data-access`, following the existing production data-access pattern.

Expected responsibility:

```text
ProductionGenealogyApiService
  -> GET /api/v1/production/genealogy/batches/{batchId}?direction={direction}
```

Keep wire DTOs in `features/production/data-access` and preserve backend field names and enum values exactly.

Do not place `HttpClient` directly in page/presentation components. Do not create a generic graph repository, global genealogy store, or alternate frontend batch domain model.

## View behavior

### 1. Resolve route identity

Read `batchId` from the route and request the genealogy using stable UUID identity.

Invalid/missing route input may be treated as a client request error without issuing a malformed HTTP request. Do not attempt to resolve a batch by lot code or item name.

### 2. Traversal direction

Provide an operator control for the three backend-supported directions with pt-BR labels while preserving wire values:

- `BOTH` — `Ambos os sentidos`;
- `UPSTREAM` — `Origem / anteriores`;
- `DOWNSTREAM` — `Uso / posteriores`.

Initial loading should use `BOTH`, matching the complete operator-tracing use case and backend default semantics.

Changing direction reloads the authoritative response from the backend. Do not filter a previously fetched `BOTH` graph locally as a substitute for the API traversal contract.

### 3. Root batch

Always make the requested `rootBatch` visibly identifiable, including:

- item name;
- lot code when present;
- batch identity where useful for traceability;
- received/expiration dates when present;
- backend-provided origin.

Map origin to clear pt-BR presentation, visibly distinguishing external/root batches from internally produced batches. Origin is display semantics supplied by the backend; Angular must not infer it from supplier presence, lot-code format, or relationship shape.

### 4. Recursive relationship rendering

Render `upstream[]` and `downstream[]` separately when the selected/backend-returned direction includes them.

Use a simple expandable/tree-like hierarchy implemented with existing Angular Material/CDK/native semantic controls. No graph/chart dependency is permitted.

For every edge, show enough backend response data to understand the relationship, including:

- production/execution context;
- source batch item/lot context;
- output batch item/lot context;
- exact `consumedQuantity` from the response;
- production date where useful.

Render `edge.next[]` recursively with no hard-coded depth limit and without assuming raw material -> intermediate -> finalized product.

Recursive presentation is allowed; recursive graph construction is not. The renderer must preserve every edge supplied by the backend, including:

- multiple source-batch relationships;
- branching downstream descendants;
- repeated participation of a batch in distinct execution edges when returned by the backend.

Do not use lot code, item name, formula ID, or display values as Angular graph identity. Stable backend IDs should be used for tracking/keys where identity is required.

### 5. Expand/collapse accessibility

Expandable controls must be keyboard operable and expose their expanded/collapsed state semantically. Prefer native buttons plus Material/CDK primitives already installed rather than custom pointer-only behavior.

Do not collapse the root identity itself. It is acceptable for recursive branches to default expanded or collapsed if the behavior remains understandable and tested; choose the simplest implementation that preserves arbitrary-depth access.

## Loading, empty, not-found, and error states

Use existing shared loading/error/empty primitives where they fit.

Explicitly handle:

- initial loading;
- direction-change loading;
- empty/no-related-production state;
- `BATCH_NOT_FOUND`;
- other backend errors.

A known batch with both `upstream` and `downstream` empty is not an error. Show a pt-BR empty state explaining that no internal production relationship was found for the batch.

For one-direction queries, an empty selected side is likewise a valid result. Do not infer missing relationships by calling unrelated inventory/movement endpoints.

Use `mapHttpError`, `UiError`, `localizeUiError`, and shared error components. Existing `BATCH_NOT_FOUND` localization should be reused.

## State and component design

Keep state local to the genealogy feature/view. Signals are appropriate for selected direction, expansion state, and view state; RxJS may be used for route/API composition.

Business rules and graph traversal discovery remain backend-authoritative.

Do not introduce:

- NgRx;
- graph/chart libraries;
- client graph-normalization infrastructure;
- speculative caching/global state;
- production/inventory business rules in components.

If recursive markup becomes unwieldy, a focused recursive presentation component is permitted inside the production feature. Its responsibility must be rendering an already-authoritative `GenealogyEdgeDto` tree, not deriving genealogy.

## UX and accessibility

All operator-facing headings, traversal labels, relationship/origin labels, loading/empty/error copy, expand/collapse accessibility text, and actions introduced by #154 must be pt-BR.

The view must remain usable on smaller screens. Prefer cards/stacked hierarchy for genealogy branches rather than a wide graph/table that requires desktop width.

Preserve keyboard operation, visible focus, semantic headings/buttons, and the project's accessibility expectations.

## Testing

Add focused behavior tests for the functionality owned by #154.

### Data access

Cover:

- exact genealogy GET URL/path;
- `batchId` route identity usage;
- exact `UPSTREAM`, `DOWNSTREAM`, and `BOTH` query wire values;
- recursive response typing, including `next[]` and exact consumed quantities.

### Routing and discoverability

Cover:

- lazy `/production/genealogy/batches/:batchId` route;
- navigation from an inventory batch row using its stable `batchId`.

### Genealogy view

Cover at minimum:

- root batch context;
- multi-level upstream chain rendering;
- downstream branching without dropped edges;
- multiple source-batch relationships;
- external/root versus internally produced origin labels;
- exact `consumedQuantity` displayed from backend data;
- recursive `next[]` rendering beyond a fixed number of levels;
- traversal direction changes causing backend reload with the selected wire value;
- empty/no-related-production state;
- `BATCH_NOT_FOUND`/not-found presentation;
- generic backend error state;
- keyboard/semantic expand-collapse behavior where expansion is implemented.

Prefer behavior-focused assertions over snapshots or assertions against private implementation details.

## Acceptance criteria

- [ ] A lazy `/production/genealogy/batches/:batchId` view exists inside the production feature and uses stable batch identity.
- [ ] Existing inventory batch rows expose a discoverable `Rastrear genealogia` action without introducing a parallel batch-search workflow.
- [ ] The view consumes only the backend genealogy API from #151 for relationship traversal.
- [ ] `UPSTREAM`, `DOWNSTREAM`, and `BOTH` preserve the backend wire contract and direction changes reload authoritative data.
- [ ] Arbitrary backend-provided recursive depth in `next[]` is renderable without a fixed production-level assumption.
- [ ] Branching descendants and multiple source-batch relationships are preserved without client-side edge inference or loss.
- [ ] Exact consumed quantities are displayed directly from backend response data and never recalculated from stock movements.
- [ ] Lot codes/item names remain display context only; stable IDs are used for route/identity/tracking.
- [ ] External/root and internally produced origins are visibly distinguished in pt-BR from backend-provided origin data.
- [ ] Loading, empty, not-found, and backend error states are explicit.
- [ ] Expandable genealogy interaction is keyboard/focus accessible and the view remains usable on smaller screens.
- [ ] No production/inventory business rule, genealogy discovery policy, graph dependency, NgRx, or speculative global state is introduced.
- [ ] Tests cover upstream recursion, downstream branching, multiple sources, external root, direction reload, empty genealogy, not-found, and generic error behavior.
- [ ] `pnpm lint`, `pnpm test`, and `pnpm build` succeed.

## Out of scope

- Any backend production, inventory, catalog, persistence, or API-contract change.
- Reconstructing genealogy from movements, notes, lot codes, dates, or local heuristics.
- Creating a second batch model or client-owned genealogy source of truth.
- QR/barcode traceability.
- Recall automation or supplier notifications.
- Impact scoring/analytics.
- Production history editing, reversal, or correction.
- Graph/chart visualization libraries.
- Global navigation redesign.
- New frontend dependencies or speculative global state infrastructure.

## Implementation guidance

Implement the smallest complete frontend delta that satisfies issue #154 and this specification.

Reuse the existing production feature structure, inventory operational batch list, shared HTTP error model, shared loading/error/empty components, Angular Material/CDK, and existing formatting helpers before adding new abstractions.

Before finalizing implementation:

1. run `pnpm lint`;
2. run `pnpm test`;
3. run `pnpm build`;
4. review `git diff` for scope, accidental client-side genealogy/business logic, and unrelated changes;
5. review `git status --short` for unintended files.
