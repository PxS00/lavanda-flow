# Issue #153 — Register internal production

## Objective

Implement the V1 Angular workflow for registering one completed internal production execution through the existing atomic backend production contract.

The frontend captures operator input and exact source-batch allocations, provides a review step, submits the authoritative request, and presents the definitive backend result. It must not reproduce formula scaling, stock eligibility, expiration, FEFO, locking, production lot sequencing, or stock mutation rules.

## Source of truth

Apply, in order:

- GitHub issue #153 for product intent, scope, and acceptance criteria;
- `AGENTS.md` and `frontend/AGENTS.md` for repository and Angular rules;
- `docs/product/scope-v1.md` for durable V1 behavior;
- ADR 0009 (`docs/architecture/decisions/0009-define-v1-production-module-boundaries.md`) for production/inventory ownership;
- the current backend code/OpenAPI for HTTP wire contracts and stable error behavior;
- this specification for the implementation-specific frontend delta of #153.

Do not reinterpret backend contracts in Angular and do not introduce backend changes as part of this issue.

## Existing contracts to reuse

### Production

Production formulas already use:

```text
GET /api/v1/production/formulas
GET /api/v1/production/formulas/{formulaId}
```

Completed production registration already uses:

```text
POST /api/v1/production/executions
```

The registration request contains exactly the current backend fields:

- `formulaId`;
- `outputQuantity`;
- `sourceAllocations[]` with concrete `batchId` and positive `quantity`;
- `productionDate`;
- `outputReceivedAt`;
- optional `outputExpiresAt`;
- `lotCodeMode` with wire values `GENERATED` or `MANUAL`;
- optional `manualLotCode`, subject to backend semantics.

Output and allocation quantities are positive decimals with at most 13 integer digits and 6 fractional digits in the current backend boundary.

The successful response is authoritative and currently contains:

- `executionId`;
- `formulaId`;
- `outputInventoryItemId`;
- `outputBatchId`;
- `outputQuantity`;
- definitive `lotCode`;
- `lotCodeMode`;
- production/output dates;
- `completedAt`;
- exact backend-confirmed consumptions.

Do not add a production preview, sequence reservation, or production read endpoint in this issue.

### Catalog and inventory reads

Reuse the existing frontend services and backend APIs rather than introducing production-specific copies of catalog or inventory data:

- `ProductionFormulaApiService` for formula definitions;
- `InventoryItemApiService` for item identity, names, units, and catalog context;
- `InventoryItemOperationsApiService.getBatches(...)` for current batch read models;
- `InventoryItemOperationsApiService.getOverview(...)` when refreshed item-level stock context is useful after success.

`BatchInventoryEntryDto.status`, quantities, expiration dates, and balances are backend-provided read facts. Angular may display those values and use them to guide the operator, but must not calculate expiration, FEFO, availability, or eligibility itself. The production POST always performs the definitive validation again.

## Feature placement and routing

Keep the workflow inside the existing Angular production feature:

```text
frontend/src/app/features/production/
├── data-access/
├── pages/
└── production.routes.ts
```

Do not create another top-level production feature or a separate frontend domain model for inventory.

Add one lazy production-registration route:

```text
/production/executions/new
```

The existing `/production/formulas` routes remain unchanged. Provide a clear operator action such as `Registrar produção` from the production area so the execution workflow is discoverable without restructuring the global navigation architecture.

## Data-access boundary

Add the smallest typed production-execution HTTP client required by #153, following the existing production formula data-access pattern.

Expected responsibility:

```text
ProductionExecutionApiService
  -> POST /api/v1/production/executions
```

Keep request/response DTOs in `features/production/data-access` and preserve backend field names and wire values exactly.

Do not place `HttpClient` calls directly in the page component and do not create a generic repository/state layer that is not required by this workflow.

## Registration workflow

### 1. Load production context

The page must explicitly support loading, loaded, empty/unavailable, and error states.

Load the available formula definitions through the existing formula API. Resolve human-readable item context through the catalog API rather than storing a second production-specific item catalog.

When a formula is selected:

1. resolve its output and ingredient item context as needed;
2. load current batch read models for each required ingredient through the existing inventory API;
3. render backend-provided lot, quantity, expiration, and operational status information to help the operator choose exact source batches.

Do not calculate the scaled ingredient requirements in Angular. The formula's persisted reference quantities may be displayed as context, but the backend remains the only authority for scaling requirements to `outputQuantity`.

### 2. Capture execution input

Use Reactive Forms.

Capture:

- selected formula;
- positive output quantity compatible with the backend decimal boundary;
- production date;
- output received date;
- optional output expiration date;
- generated/manual lot-code mode;
- one or more exact source-batch allocations for each ingredient as required by the operator's real production execution.

Client validation is limited to request shape and usability, for example required fields, positive values, supported decimal precision, distinct concrete batch IDs, and manual-field presence when the selected mode requires it.

Do not use frontend validation to claim that stock is sufficient, a batch is definitively eligible, allocations satisfy the scaled formula, or a lot code is definitively available.

A formula ingredient may use multiple concrete source batches. The page may group allocation rows by ingredient for usability, but the submitted request must flatten them to the backend `sourceAllocations[]` wire contract.

The same concrete batch ID must not be submitted twice in one request because the current backend contract requires distinct source-batch IDs. This is request-shape validation, not a stock-allocation policy.

### 3. Lot-code mode

Expose operator-facing pt-BR labels while preserving the exact wire values:

```text
GENERATED
MANUAL
```

For `GENERATED`:

- do not calculate `TTT-EEE-LLL-MM-YYYY` in Angular;
- do not query or infer the next `LLL`;
- do not reserve any sequence;
- do not display a locally authoritative lot code;
- explain that the definitive lot is assigned only when the backend successfully registers production.

A lot preview is not required by #153. Omitting it is preferred to introducing non-authoritative sequence logic.

For `MANUAL`, show the manual lot field and send its value according to the existing backend contract. Do not validate genealogy or derive semantic meaning from the manual lot string.

### 4. Review before confirmation

Before the POST, provide an explicit review state showing the operator-entered data:

- selected formula/output item context;
- output quantity;
- dates;
- selected source batches and entered quantities;
- selected lot-code mode and manual value when applicable.

The review is a confirmation of input, not a backend validation preview. It must not display locally calculated stock-after-production, scaled recipe requirements, guaranteed eligibility, or guaranteed generated lot sequence.

The production POST is sent only after explicit confirmation.

### 5. Submission

While the request is in flight:

- disable the confirmation action;
- guard the submit handler against repeated invocation;
- send exactly one POST per explicit confirmation;
- do not mutate local stock or mark production as completed optimistically.

If the backend rejects the request, preserve useful operator input where practical and show the error through the shared frontend error model. The operator may correct the request and explicitly submit again.

### 6. Backend-confirmed success

Only the successful `ProductionExecutionResponse` marks the operation as completed.

Display at minimum:

- definitive internal `lotCode`;
- `outputBatchId`;
- backend-confirmed `outputQuantity`;
- output item context;
- relevant production dates;
- execution identifier and/or confirmed source consumptions where they improve operational clarity.

Never reconstruct the output batch, lot sequence, movements, or resulting balances from the submitted request.

## Backend-confirmed refresh

After a successful production POST, refresh affected inventory state from the backend for:

- each distinct source inventory item represented by the selected source batches; and
- the `outputInventoryItemId` returned by the production response.

Use existing inventory read endpoints. Do not create or update a client-side inventory cache by subtracting/adding submitted quantities.

A successful production POST is irreversible from the perspective of this issue. If a subsequent refresh request fails:

- keep the production operation in a successful/completed state;
- retain and display the authoritative production response;
- show an actionable secondary message that current stock data could not be refreshed;
- offer a safe retry of the read refresh only;
- never convert the production into a failed state and never re-enable a production resubmission that could create a duplicate execution.

This distinction between **production failure** and **post-success refresh failure** must be explicit in component state and tests.

## Error handling

Reuse `mapHttpError`, `UiError`, `localizeUiError`, and existing shared error/loading primitives. Do not add a page-local parallel error taxonomy.

Extend shared pt-BR localization where the current backend stable codes used by this workflow are not yet covered. Relevant current cases include at least:

- `PRODUCTION_FORMULA_NOT_FOUND`;
- `INVALID_PRODUCTION_ALLOCATION`;
- `BATCH_NOT_FOUND`;
- `EXPIRED_BATCH`;
- `INSUFFICIENT_STOCK`;
- standard validation/malformed-request codes.

Use backend-provided error details when useful for actionable feedback, but do not calculate replacement business-rule results in Angular. Unknown codes continue through the shared generic fallback by error kind.

## State and component design

Keep state local to the production registration workflow unless a concrete existing shared state already owns it.

Use Signals for local/derived UI state and RxJS where asynchronous loading/refresh composition justifies it. Keep HTTP access in data-access services and avoid business rules in components.

Model the workflow explicitly enough to prevent invalid transitions, especially:

```text
editing -> review -> submitting -> success
                     \-> backend rejection -> editing/review
success -> refreshing -> success
```

A refresh failure remains under the success branch and must not transition back to a submit-ready production state.

Do not introduce NgRx or another state-management dependency.

## UX and accessibility

All operator-facing text, labels, validation feedback, confirmation copy, status messages, and accessibility text introduced by this issue are pt-BR.

Use existing Angular Material/CDK and shared UI patterns. The workflow must remain usable on smaller screens: allocation rows/cards must not depend on a wide desktop table to remain operable.

Preserve keyboard operation, visible focus, semantic labels, error associations, and WCAG AA expectations from `frontend/AGENTS.md`.

## Testing

Add focused tests for the behavior owned by #153.

### Data access

Cover:

- `POST /api/v1/production/executions` URL;
- exact request field/wire-value mapping;
- successful response mapping/typing.

### Routing and loading

Cover:

- `/production/executions/new` route;
- formula loading and formula selection;
- ingredient batch loading through existing inventory services;
- loading/error/empty or unavailable states that can occur before registration.

### Workflow

Cover at minimum:

- generated lot mode without frontend sequence calculation;
- manual lot mode and manual value submission;
- multi-batch allocation for one ingredient;
- explicit review before submission;
- no POST before confirmation;
- duplicate submit protection while the POST is in flight;
- backend validation/allocation rejection;
- insufficient-stock response;
- expired-batch response;
- successful registration showing the backend definitive lot/output batch/quantity;
- source/output inventory refresh after success;
- no optimistic stock reconstruction;
- refresh failure after successful production remains a successful production and retries only the read refresh.

Prefer behavior-focused tests over brittle snapshots or implementation-detail assertions.

## Acceptance criteria

- [ ] A lazy `/production/executions/new` workflow exists inside the current production feature and is discoverable from the production area.
- [ ] Required formula, catalog, and inventory context is loaded through existing backend APIs/services.
- [ ] The operator can select a formula and enter a positive output decimal compatible with the current backend contract.
- [ ] One or more exact source batches can be allocated per formula ingredient and are flattened to the existing `sourceAllocations[]` request contract.
- [ ] Formula scaling, FEFO, expiration, stock eligibility, balance validation, locking, and lot sequencing are never reimplemented in Angular.
- [ ] Generated and manual lot modes preserve `GENERATED` / `MANUAL` wire values and generated mode never reserves or calculates `LLL`.
- [ ] An explicit input review occurs before confirmation without claiming backend validation success.
- [ ] Duplicate production submission is prevented while the request is in flight.
- [ ] No stock/production success state is applied optimistically.
- [ ] Backend success displays the definitive lot code, output batch identity, output quantity, and relevant returned execution summary.
- [ ] Affected inventory state is refreshed from backend reads after success rather than reconstructed locally.
- [ ] Post-success refresh failure does not turn the production into a failed/re-submittable operation.
- [ ] Relevant backend validation/not-found/insufficient/expired failures use the shared frontend error model and actionable pt-BR copy.
- [ ] Operator-facing content and accessibility text are pt-BR and the workflow remains usable on desktop and smaller screens.
- [ ] Tests cover generated/manual modes, multi-batch allocation, review, successful registration, backend failures, in-flight protection, backend-confirmed refresh, and refresh-failure semantics.
- [ ] `pnpm lint`, `pnpm test`, and `pnpm build` succeed.

## Out of scope

- Any backend production, catalog, inventory, persistence, or API-contract change.
- Formula setup behavior beyond the already completed #152 workflow.
- Frontend formula scaling or automatic unit conversion.
- FEFO/source-batch auto-selection.
- Frontend-owned expiration or stock-eligibility rules.
- Generated-lot sequence preview/reservation/calculation.
- Production history/list endpoint or UI not already required for this registration result.
- Recursive genealogy visualization.
- Production reversal/cancellation.
- Costing, purchasing, planning, scheduling, or broader manufacturing automation.
- New frontend dependencies or speculative global state infrastructure.

## Implementation guidance

Reuse the current production formula feature structure, catalog/inventory data-access services, shared HTTP error model, shared loading/error/empty components, and Angular Material patterns before adding new abstractions.

Implement the smallest complete frontend delta that satisfies this specification. Do not modify backend production semantics to make frontend tests easier, and do not pre-build later genealogy or production-history work.

Before finalizing implementation:

1. run `pnpm lint`;
2. run `pnpm test`;
3. run `pnpm build`;
4. review `git diff` for scope and architecture violations;
5. review `git status --short` for unintended files.
