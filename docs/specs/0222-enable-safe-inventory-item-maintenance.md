# Issue #222 — Enable safe inventory item maintenance

## Status

Implementation specification for issue #222.

## Source of truth

GitHub issue #222 remains the source of truth for product intent, scope, acceptance criteria, constraints, and out-of-scope work. This specification refines the implementation contract against the current `develop` baseline.

The implementation must also follow:

- `AGENTS.md`;
- `backend/AGENTS.md` for backend-specific rules;
- `frontend/AGENTS.md` for frontend-specific rules;
- `docs/specs/0229-model-finished-products-and-shared-fragrance-references.md` for `FINISHED_PRODUCT`, `essenceReference`, and `gender` semantics;
- `docs/architecture/architecture.md` and `docs/architecture/backend-structure.md` for module boundaries;
- `docs/architecture/inventory-package-structure.md` for inventory ownership;
- `docs/architecture/decisions/0007-standardize-api-error-contract.md` for structured errors;
- the current backend, frontend, Flyway schema, and generated OpenAPI contract for existing behavior.

If an implementation detail is not specified here, preserve current behavior and choose the smallest complete change that satisfies issue #222.

## Branch

```text
feature/222/enable-safe-inventory-item-maintenance
```

The branch starts from the current `develop` baseline.

## Objective

Allow an authenticated operator to safely maintain an existing catalog item through the supported API and Angular catalog workspace without database access, destructive history changes, or generic CRUD expansion.

This issue adds a focused maintenance vertical slice. It does not change the catalog model introduced by #229 and does not move inventory or production rules into `catalog` or the frontend.

## Current baseline

The current implementation already provides:

- `POST /api/v1/inventory-items` for catalog registration;
- `GET /api/v1/inventory-items/{inventoryItemId}` for detail;
- `GET /api/v1/inventory-items` for search;
- catalog domain methods for rename, description changes, activation/deactivation, and one-time stable metadata assignment;
- `Category.FINISHED_PRODUCT` from #229;
- `essenceReference` as the existing stable fragrance-reference wire field;
- `productionTypeCode` as the existing stable production-family metadata;
- optional `ProductGender` metadata for `ESSENCE` and `FINISHED_PRODUCT`;
- immutable batch and stock-movement history in the `inventory` module;
- minimum-stock configuration as a separate inventory-owned capability under `/api/v1/inventory/items/{inventoryItemId}/minimum-stock-level`.

Issue #222 must build on these contracts instead of duplicating them.

## Resolved scope decisions

### Supported mutable fields

The generic catalog maintenance contract supports exactly:

- `name`;
- optional `description`;
- `active`;
- `essenceReference` under its existing one-time assignment/replay rules;
- `productionTypeCode` under its existing one-time assignment/replay rules.

The update contract must not expose these existing fields as editable:

- `id`;
- `category`;
- `unitOfMeasure`;
- `gender`.

`category` and `unitOfMeasure` are explicitly outside #222. `gender` was introduced by #229 but is not part of #222's approved maintenance scope and must remain unchanged by this generic update flow.

Minimum-stock configuration is also not part of the catalog update payload. It remains owned by the existing inventory minimum-stock API and UI capability. Do not duplicate `minimumQuantity` into catalog persistence or into the #222 request.

### Stable metadata semantics

`essenceReference` remains the exact existing API/wire name and accepts only `001` through `999`; `000` remains reserved for the no-fragrance production-lot case.

For both `essenceReference` and `productionTypeCode`:

- current null + request null: keep null;
- current null + valid request value: assign once;
- current assigned + same request value: accept as idempotent replay;
- current assigned + null request: reject removal;
- current assigned + different request value: reject replacement.

For `essenceReference`, preserve #229 category/reuse rules:

- `ESSENCE` and `FINISHED_PRODUCT` are the only eligible categories;
- canonical `ESSENCE` references remain unique;
- multiple `FINISHED_PRODUCT` items may share the same reference;
- maintenance must not restore global reference uniqueness;
- no category migration is introduced to work around reference rules.

The backend domain/application layer remains authoritative. The frontend may disable immutable fields after assignment for UX, but backend code and PostgreSQL constraints must remain the actual protection.

### No reference-search expansion in #222

Issue #222 says stable-reference search may be added when it can be done without changing #229 semantics. It is not required by an acceptance criterion that cannot otherwise be satisfied, and the current operator maintenance workflow can be completed without expanding the search contract.

Therefore this implementation must preserve the existing name/category/active search behavior and must not add new search filters unless the current code proves they are strictly necessary for the edit flow.

### No schema migration expected

The current schema already persists every field required by this issue and already contains the stable-reference constraints from #146/#229.

No Flyway migration is expected. Add one only if inspection of the actual current schema proves a missing persistence invariant required by #222; do not modify historical migrations.

## Backend contract

### Application use case

Add one focused catalog application use case, expected conceptually as:

```text
UpdateInventoryItem
UpdateInventoryItemCommand
```

The command should carry only the supported update fields plus the item identifier as appropriate to the existing application style.

The use case must:

1. load the existing `InventoryItem` by UUID through the catalog repository/port;
2. return the existing structured not-found behavior when the item does not exist;
3. apply name and description through domain behavior;
4. apply stable metadata through the existing domain assignment rules rather than replacing fields directly;
5. activate or deactivate explicitly from the requested state;
6. persist the same aggregate identity;
7. return the normal `InventoryItemResult`/response representation;
8. execute transactionally.

Do not place maintenance rules in the controller.

Do not introduce a second catalog aggregate, generic patch framework, reflection-based updater, event, or speculative abstraction.

### Domain

Reuse and strengthen the existing `InventoryItem` behavior where necessary.

Required invariants:

- UUID never changes;
- category never changes in this use case;
- unit of measure never changes in this use case;
- gender never changes in this use case;
- assigned stable metadata cannot be changed or removed;
- unchanged stable metadata replay succeeds;
- invalid stable metadata is rejected before persistence where the existing domain can do so;
- activation/deactivation does not rewrite stock or history.

If current `IllegalArgumentException`/`IllegalStateException` behavior does not map cleanly to the repository's structured API error contract, introduce the smallest catalog business exception(s) needed to expose stable, user-safe codes. Do not leak raw internal exception messages as the public maintenance contract.

### HTTP

Add one supported full-maintenance endpoint:

```text
PUT /api/v1/inventory-items/{inventoryItemId}
```

Use `PUT` rather than a generic JSON Patch contract because the maintenance surface is small, explicit, and intentionally constrained.

Expected request shape:

```json
{
  "name": "Lavanda Premium",
  "description": "Optional operator description",
  "active": true,
  "essenceReference": "014",
  "productionTypeCode": "PRF"
}
```

The request must not contain editable `id`, `category`, `unitOfMeasure`, or `gender` fields.

Validation must preserve existing limits and wire formats, including:

- non-blank name, maximum 255 characters;
- optional description normalized consistently with registration/domain behavior;
- non-null `active` request state;
- optional `essenceReference` shape `001`–`999`;
- optional `productionTypeCode` exactly three uppercase letters.

Successful update returns HTTP `200` with the normal inventory-item response body.

Document/infer at least:

- `200` success;
- `400` malformed/bean-validation request;
- `404` unknown inventory item;
- the existing business-error status/code convention for forbidden stable metadata change/removal or invalid category/reference assignment.

Keep authentication/CSRF behavior consistent with the existing secured mutation endpoints.

### Persistence

Use the existing catalog repository/JPA mapping.

The update must modify the existing row rather than replace identity or create a second item. Existing batches, movements, formulas, production records, lot codes, and genealogy references must continue pointing to the same UUID.

Do not access inventory persistence from the catalog module to prove this. Integration tests may verify unchanged related data through supported module behavior or database-level test fixtures where already appropriate, but production code must preserve module boundaries.

## Frontend contract

### Routing and navigation

Extend the existing catalog feature with a dedicated edit route, expected as:

```text
/catalog/:inventoryItemId/edit
```

Add an explicit touch-friendly edit action from the item detail view. Use a real button/link with an accessible label; do not rely on hover-only affordances.

Do not turn the list into spreadsheet-style inline editing.

### Edit page

Add a focused catalog edit page that:

1. loads the item by UUID through `InventoryItemApiService`;
2. shows loading/error/retry states using existing shared UI patterns;
3. pre-fills the form with current item data;
4. allows editing name and optional description;
5. allows active/inactive state changes;
6. allows one-time stable metadata assignment when currently null and eligible;
7. presents assigned stable metadata as immutable/read-only after assignment;
8. shows category, unit, and gender as non-editable context when useful, not as mutable controls;
9. submits the explicit update DTO through the data-access service;
10. maps field/business/infrastructure failures through the existing centralized error utilities;
11. on success, navigates to or refreshes the item detail so the operator immediately sees persisted state.

All user-visible copy must be in pt-BR.

Reuse existing catalog display options, field validation conventions, and Angular Material components where practical. Keep HTTP access out of presentation-only helpers and do not duplicate backend inventory/production rules.

### Data access

Extend the existing typed catalog data-access layer with an explicit update request type, for example:

```ts
interface UpdateInventoryItemRequest {
  readonly name: string;
  readonly description: string | null;
  readonly active: boolean;
  readonly essenceReference: string | null;
  readonly productionTypeCode: string | null;
}
```

and an API method equivalent to:

```text
update(inventoryItemId, request) -> PUT /api/v1/inventory-items/{inventoryItemId}
```

Do not reuse the registration request because registration exposes category, unit, and gender fields that maintenance must not make editable.

## Error behavior

Preserve ADR 0007 and existing frontend error mapping.

The UI must distinguish at least:

- validation errors that can be shown at the affected field;
- catalog/business rule rejection for stable metadata mutation;
- not-found state;
- infrastructure/unexpected failure.

Do not convert a backend business rejection into optimistic local state. The displayed item should only change after a successful response.

## Module ownership

- `catalog` owns item metadata, active state, stable references, and the maintenance use case.
- `inventory` continues to own batches, balances, movements, FEFO, minimum-stock levels, and expiration behavior.
- `production` continues to consume catalog metadata through public module APIs only.
- Angular consumes the supported REST contract and does not recompute backend rules.
- No module boundary may be crossed through another module's `infrastructure` package.

Spring Modulith verification must remain green.

## Tests

### Backend domain/application

Cover at minimum:

- valid rename and description update;
- description clearing/normalization according to existing semantics;
- activation and deactivation;
- same UUID before/after update;
- item not found;
- assign previously-null `essenceReference` where category allows it;
- accept unchanged `essenceReference` replay;
- reject assigned reference change;
- reject assigned reference removal;
- reject invalid/ineligible reference assignment;
- preserve #229 duplicate-reference behavior for separate `FINISHED_PRODUCT` items;
- assign previously-null `productionTypeCode`;
- accept unchanged production-code replay;
- reject production-code change/removal;
- category/unit/gender remain unchanged because the update command cannot mutate them.

### Persistence/integration

Cover the supported update path against PostgreSQL/Testcontainers where existing test organization makes this appropriate:

- updated metadata survives reload;
- UUID is preserved;
- existing stable-reference database constraints remain effective;
- existing batch/movement relationships are not rewritten by catalog maintenance;
- no new Flyway migration is required unless implementation discovers a real missing invariant.

### HTTP/security

Cover:

- authenticated `PUT` success;
- validation failures;
- not-found response;
- stable metadata assignment/replay/rejection;
- exact response representation;
- CSRF/security behavior consistent with existing mutation endpoints;
- registration, detail, and search contracts remain compatible.

### Frontend

Cover:

- edit route/detail edit action;
- loading existing item and pre-filled form;
- successful name/description update;
- activation and deactivation;
- one-time stable metadata assignment;
- assigned stable metadata displayed as immutable;
- backend field validation localization;
- business-error presentation;
- not-found/infrastructure failure and retry behavior;
- submit-disabled/in-flight behavior preventing accidental duplicate writes;
- successful navigation/refetch showing persisted data;
- touch-usable edit action with no hover dependency.

Extend the existing operational workflow tests only where the edit flow materially belongs there; do not duplicate every page test in end-to-end-style specs.

## Existing behavior that must not change

- current registration endpoint and DTO semantics;
- current item detail/search contracts except additive behavior strictly required by #222;
- `FINISHED_PRODUCT`, product gender, shared `essenceReference`, and reference uniqueness semantics from #229;
- `productionTypeCode` stability;
- generated production lot behavior;
- inventory balances, batches, movements, FEFO, expiration, and minimum-stock ownership;
- production formulas, executions, packaged-filling semantics, and genealogy;
- PostgreSQL as source of truth and Flyway as schema authority;
- API error envelope;
- authentication and CSRF model.

## Out of scope

Do not implement:

- hard deletion;
- generic CRUD;
- category migration;
- unit-of-measure changes;
- gender maintenance;
- batch or movement editing;
- stock adjustments from the catalog edit page;
- minimum-stock redesign or duplication;
- automatic unit conversion;
- bulk/spreadsheet editing;
- sales/orders/customers/pricing;
- new production or genealogy behavior;
- #230 grouped stock navigation;
- #224 protected deletion;
- new libraries or state-management frameworks;
- new events or modules.

## Acceptance checklist

- [ ] Existing item can be opened in a mobile/touch-friendly edit flow with current values pre-filled.
- [ ] Name and optional description can be maintained through Angular and the supported backend contract.
- [ ] Active/inactive state can be maintained through Angular and is reflected by existing catalog search filtering.
- [ ] Item UUID remains unchanged.
- [ ] Stable `essenceReference` follows one-time assignment, replay, immutability, `001`–`999`, and #229 reuse semantics.
- [ ] Stable `productionTypeCode` follows one-time assignment, replay, and immutability semantics.
- [ ] Category, unit, and gender are not made generally editable.
- [ ] No hard-delete capability is added.
- [ ] Existing batch/movement/production history remains unchanged and attached to the same catalog identity.
- [ ] Structured API errors and pt-BR UI feedback cover validation, business, not-found, and infrastructure failures.
- [ ] OpenAPI accurately represents the maintenance endpoint.
- [ ] Backend and frontend tests cover the complete maintenance vertical slice.
- [ ] Spring Modulith verification remains green.
- [ ] No unrelated issue scope is implemented.

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

Repository review:

```bash
git diff --check
git diff
git status --short
```

Known non-blocking warnings must be reported rather than silently treated as failures or fixed outside issue scope.

## Implementation sequence

1. Read issue #222, repository/backend/frontend `AGENTS.md`, this spec, #229 spec, ADR 0007, and current catalog tests/contracts.
2. Add the focused backend update command/use case and tests around existing domain mutation rules.
3. Add the explicit `PUT /api/v1/inventory-items/{inventoryItemId}` request/response contract and web/security tests.
4. Confirm persistence and PostgreSQL constraints; do not create a migration unless the current schema truly requires one.
5. Extend the Angular typed API client and DTOs.
6. Add the dedicated edit route/page and detail-page action with pt-BR copy, mobile/touch behavior, and centralized errors.
7. Add focused frontend tests and required operational-flow coverage.
8. Run full backend/frontend validation.
9. Review the complete `git diff` and `git status --short` for scope leaks before proposing commits or PR work.
