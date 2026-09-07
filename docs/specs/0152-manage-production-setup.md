# Issue #152 — Manage production setup

## Objective

Implement the V1 Angular setup workflow for catalog production metadata and production formulas using the contracts already present on the branch.

## Source of truth

Apply `AGENTS.md`, `frontend/AGENTS.md`, and `docs/product/scope-v1.md`. This file defines only the implementation-specific delta for #152. Existing backend code/OpenAPI defines the current wire contracts; do not duplicate or reinterpret them in Angular.

Relevant backend entry points:

- catalog: `POST /api/v1/inventory-items`, `GET /api/v1/inventory-items/{inventoryItemId}`, `GET /api/v1/inventory-items`;
- formulas: `POST /api/v1/production/formulas`, `GET /api/v1/production/formulas`, `GET /api/v1/production/formulas/{formulaId}`, `PUT /api/v1/production/formulas/{formulaId}`.

## Current contract boundary

Catalog currently has no HTTP update operation for an existing inventory item. Therefore #152 must not invent a frontend edit flow that cannot be persisted or silently add a backend catalog mutation outside this issue.

For production metadata in this issue:

- extend inventory-item registration to submit the optional `essenceReference` and `productionTypeCode` fields already supported by the catalog registration contract;
- display persisted production metadata in the existing item detail flow after backend retrieval;
- preserve null values for items that do not participate in production;
- never generate, recycle, infer, or locally assign production references.

Retroactive assignment/change of production metadata for an already persisted item requires an explicit backend HTTP contract and is outside #152 unless separately specified.

## Required behavior

### Catalog production metadata

- Add the existing optional production-reference fields to the inventory-item registration UI and request DTO mapping.
- Keep the fields optional and preserve the backend wire names/values.
- Present persisted values in inventory-item detail when present and an appropriate pt-BR absence state when not assigned.
- Frontend validation may enforce input shape only; backend validation remains authoritative.

### Production formulas

Add a production setup capability following existing feature/data-access/page/routing conventions.

It must allow an operator to:

- list formulas;
- inspect one formula;
- create a formula;
- edit an existing formula;
- choose one output inventory item;
- define one or more ingredient requirements;
- add/remove ingredient rows without introducing duplicate frontend catalog state;
- submit decimal quantities without client-side recipe scaling or unit conversion;
- receive success state only after backend confirmation.

Use the existing catalog search/read API for item selection. Do not introduce hard-coded catalog copies or production-specific item identity.

## UI states

Formula list and form flows must explicitly handle:

- loading;
- empty list;
- transport/backend error;
- backend validation/conflict/not-found rejection;
- in-flight submission;
- backend-confirmed success.

Reuse current shared UI/error primitives where they fit.

## Testing

Cover at minimum:

- production metadata included in inventory-item registration requests;
- persisted production metadata rendered by item detail;
- formula list success, empty, and error states;
- formula create submission with output item and multiple ingredients;
- formula edit load/submission;
- ingredient add/remove behavior;
- backend validation/rejection mapping;
- no success state before backend confirmation;
- route coverage for the new production setup capability.

Do not replace focused tests with broad brittle snapshots.

## Acceptance criteria

- [ ] Inventory-item registration can submit the existing optional production metadata contract.
- [ ] Inventory-item detail displays backend-provided production metadata without deriving or generating references.
- [ ] Items without production metadata remain valid and render an explicit absence state.
- [ ] Formula list supports loading, empty, success, and error states.
- [ ] An operator can inspect, create, and edit formulas using the current production formula API.
- [ ] Formula creation/editing supports one output item and one or more ingredient requirements.
- [ ] Decimal quantities round-trip through the API without frontend-owned recipe semantics or unit conversion.
- [ ] Item selection uses current catalog API data rather than duplicated frontend identity/classification tables.
- [ ] Backend validation/conflict/not-found failures use the existing shared frontend error model where applicable.
- [ ] Successful writes are reflected only after backend confirmation.
- [ ] Operator-facing content introduced by this issue is pt-BR and accessibility requirements from `frontend/AGENTS.md` are preserved.
- [ ] Relevant tests cover the behaviors above.
- [ ] `pnpm lint`, `pnpm test`, and `pnpm build` succeed.

## Out of scope

- Backend catalog or formula implementation changes.
- Retroactive production-reference assignment/update for already persisted catalog items without an existing HTTP contract.
- Production execution or stock-consumption UI.
- Generated/manual production lot workflow.
- Genealogy visualization.
- Formula version history.
- Automatic unit conversion.
- Costing or production planning.
- New frontend dependencies or state-management frameworks.

## Implementation guidance

Inspect and reuse the current frontend catalog, routing, data-access, form, shared-state, and HTTP-error patterns before creating new abstractions. Add the smallest feature structure required by this specification; do not pre-build #153 or #154.
