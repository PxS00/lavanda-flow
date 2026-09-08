# Issue #181 — Expose audited stock maintenance actions

## Objective

Add the smallest complete Angular stock-maintenance workflow required for Céu de Lavanda operators to correct one selected inventory batch without editing history or duplicating backend inventory rules.

The frontend must expose the existing signed stock-adjustment contract plus the dedicated `LOSS` and `EXPIRED_DISPOSAL` operations delivered by #180. All stock-changing requests remain backend-authoritative, exact-decimal, auditable, and non-optimistic.

## Source of truth

Apply, in order:

1. GitHub issue #181;
2. `AGENTS.md`;
3. `frontend/AGENTS.md`;
4. `docs/specs/0171-preserve-exact-decimal-quantities.md`;
5. `docs/specs/0179-add-authenticated-operator-session.md`;
6. `docs/specs/0180-register-loss-expired-disposal-movements.md`;
7. current inventory operational workspace, API services, DTOs, error localization, and tests;
8. this specification for the approved implementation details of #181.

Issue #181 remains authoritative for objective, scope, acceptance criteria, architectural constraints, and out-of-scope behavior.

## Branch

```text
feature/181/expose-stock-maintenance-actions
```

The branch is based on `develop` after #180 was squash-merged.

## Dependency state

The issue is no longer blocked:

- #179 authenticated operator session is merged into `develop`;
- #180 explicit `LOSS` and `EXPIRED_DISPOSAL` backend operations are merged into `develop`.

No backend work is required by #181 unless the current public contract is discovered to contradict the accepted issue/spec. If that happens, stop and report the contradiction instead of changing backend scope silently.

## Current frontend architecture

The existing inventory operational workspace is:

```text
frontend/src/app/features/inventory/pages/inventory-item-operational-page/
```

It already owns:

- inventory overview reads;
- current batch reads;
- FEFO withdrawal composition;
- minimum-stock maintenance;
- paginated movement history;
- backend-confirmed refresh after FEFO withdrawal.

`InventoryItemOperationsApiService` is currently item-read/minimum-stock oriented. FEFO has its own focused write service. The new batch-level maintenance writes should follow the same focused data-access style instead of turning the item-read service into a generic inventory client.

The existing batch table already provides the concrete batch context required by #181.

## Approved UI architecture

Use a focused batch-level maintenance dialog launched from the existing batch row.

### Entry point

Add a pt-BR action such as:

```text
Manutenção
```

for each concrete batch row in the existing `Lotes` table.

The action must pass the selected backend-provided batch read model to the maintenance dialog. The operator must not be able to submit a maintenance write without an explicit selected batch.

Keep the existing genealogy action.

### Dialog

Create a focused component under the inventory feature, for example:

```text
frontend/src/app/features/inventory/ui/stock-maintenance-dialog/
```

Use Angular Material dialog primitives already available in the approved stack. Do not add a dependency.

The dialog should clearly display the selected batch context, at minimum:

- lot code or a suitable fallback;
- current quantity;
- backend-provided status;
- expiration date when present.

These fields are read-only context. They must not be used to recalculate stock eligibility.

The dialog owns only presentation/form/write orchestration for one selected batch. It must not become a generic movement editor.

## Supported operations

Expose exactly three operator choices:

1. stock correction / adjustment;
2. physical loss;
3. expired-stock disposal.

Use a frontend-only operation discriminator such as:

```text
ADJUSTMENT
LOSS
EXPIRED_DISPOSAL
```

only to drive UI behavior. Do not send a caller-selected `MovementType` to a generic endpoint.

Each choice maps to one fixed backend route.

## HTTP contracts

Create a focused batch-maintenance API service under:

```text
frontend/src/app/features/inventory/data-access/
```

A suitable name is:

```text
StockMaintenanceApiService
```

The service must use native `HttpClient`, `API_BASE_URL`, the existing `/api/v1` normalization convention, and same-origin authentication/XSRF behavior.

### Adjustment

```text
POST /api/v1/inventory/batches/{batchId}/adjustments
```

Request:

```json
{
  "quantity": "-12.500000",
  "reason": "Physical count correction"
}
```

`quantity` is a signed exact-decimal string. Positive values increase balance; negative values decrease balance. Zero is invalid.

### Loss

```text
POST /api/v1/inventory/batches/{batchId}/losses
```

Request:

```json
{
  "quantity": "12.500000",
  "reason": "Bottle broke during handling"
}
```

`quantity` is a positive exact-decimal string.

### Expired disposal

```text
POST /api/v1/inventory/batches/{batchId}/expired-disposals
```

Request:

```json
{
  "quantity": "12.500000",
  "reason": "Expired stock physically discarded"
}
```

`quantity` is a positive exact-decimal string.

### Response

The three write endpoints expose the existing stock-movement result shape:

```text
movementId
batchId
type
quantity
resultingBalance
reason
occurredAt
```

`quantity` and `resultingBalance` remain strings in Angular DTOs.

Reuse the existing `InventoryMovementType` wire values. Do not introduce numeric quantity DTOs.

## Exact-decimal rule

Exact stock quantities must remain strings from form input through request payload and backend response rendering.

Never use:

```text
Number(...)
parseFloat(...)
parseInt(...)
unary +
arithmetic coercion
DecimalPipe for exact stock quantities
```

for stock-maintenance quantities.

Use text inputs with `inputmode="decimal"` and lexical validation.

Do not normalize a valid quantity through JavaScript numeric arithmetic. Trimming surrounding whitespace is allowed.

A representative exact value such as:

```text
9999999999999.123456
```

must reach the API service unchanged.

## Form strategy

Use typed Reactive Forms for the new maintenance dialog.

Do not introduce a new Signal Forms form in this issue. Existing Signal Forms usage elsewhere in the workspace is not a reason to expand that form strategy.

The form should contain:

- operation;
- quantity;
- reason.

## Quantity validation

Frontend validation is input-shape validation only. Backend remains authoritative for business rules and persistence.

### Adjustment quantity

Accept canonical UI text equivalent to:

```text
^-?\d{1,13}(?:\.\d{1,6})?$
```

and reject values representing zero.

A leading minus sign means a balance decrease. A positive value has no required confirmation for destructiveness.

Do not require or introduce a leading plus sign.

### Loss / expired disposal quantity

Accept canonical UI text equivalent to:

```text
^\d{1,13}(?:\.\d{1,6})?$
```

and require a value greater than zero using lexical/string-safe validation.

For zero detection, a string-safe test such as checking whether the normalized decimal contains any `[1-9]` digit is acceptable. Do not convert the value to `number`.

### Reason

Reason is mandatory for all three maintenance operations.

Frontend validation must reject:

- null/empty value;
- whitespace-only value;
- more than 255 characters.

Trim surrounding whitespace before sending the request.

## Destructive confirmation

A clear pt-BR confirmation step is mandatory before every balance-decreasing request.

The following are always destructive:

- `LOSS`;
- `EXPIRED_DISPOSAL`.

A stock adjustment is destructive only when its normalized quantity string starts with `-`.

Determining that the operator entered a negative signed adjustment is presentation/request-shape logic, not an inventory eligibility calculation.

The confirmation must occur before the HTTP request is sent and should identify the selected lot and requested quantity.

A suitable confirmation state within the same dialog is preferred over opening a second nested dialog.

Example user-facing meaning:

```text
Confirmar redução de estoque

Esta operação reduzirá o saldo do lote selecionado e criará uma movimentação auditável. Deseja continuar?
```

Provide explicit `Confirmar` and `Cancelar` actions.

Positive adjustments may submit without the destructive confirmation state.

## Expired-disposal authority

Angular must not determine whether the selected batch is eligible for `EXPIRED_DISPOSAL`.

Do not:

- compare `expiresAt` with the browser date;
- calculate `today` for disposal eligibility;
- hide or disable expired disposal because `status !== 'EXPIRED'`;
- infer legal/semantic eligibility from alert state.

The backend is authoritative.

The UI may display backend-provided batch status and expiration date as context, but the operator may attempt the action and receive the backend decision.

`BATCH_NOT_EXPIRED` must be presented as actionable pt-BR feedback.

## Submission lifecycle

The dialog must have an explicit in-flight state.

While the write request is in flight:

- disable submit/confirm actions;
- prevent a second request from programmatic or repeated clicks;
- keep the selected operation/batch stable;
- do not mutate displayed batch balance optimistically.

A simple local `isSubmitting` signal/boolean plus an early-return guard is sufficient. Do not introduce a global state store.

On failure:

- keep the dialog open;
- keep operator input when practical;
- clear the in-flight state;
- present actionable pt-BR feedback through the shared UI error model;
- do not modify batch/overview/history state.

On success:

- close or complete the maintenance dialog;
- notify the parent operational page only after backend success;
- refresh authoritative reads from the backend.

## Backend-confirmed refresh

After a successful maintenance write, the inventory-item operational page must refresh:

1. overview;
2. batches;
3. movement history.

Reuse the existing request subjects/retry methods instead of introducing a second state architecture.

Reset movement history to page 0 before or during the success refresh so the newly created immutable movement is visible immediately rather than remaining hidden on a later page.

The existing alert page is a separate routed component with no shared cached alert state; it fetches fresh low-stock and expiration data on construction. Do not introduce a global alert invalidation bus or store solely for #181.

Within the item workspace, refreshing `overview` updates the backend-provided low-stock/out-of-stock/expiration summary metrics. When the operator later navigates to the standalone alerts page, that page performs fresh backend reads.

This satisfies the issue's "alerts ... where relevant" requirement without speculative global state.

## Movement history

Do not append a locally constructed movement to the movement list.

After success, refetch movement history from the backend. The backend movement remains the source of truth for:

- `movementId`;
- final type;
- exact quantity;
- reason;
- occurrence timestamp.

Existing history labels for `ADJUSTMENT_IN`, `ADJUSTMENT_OUT`, `LOSS`, and `EXPIRED_DISPOSAL` must remain intact.

## Error localization

Use `mapHttpError`, `localizeUiError`, `localizeFieldError`, `hasUnhandledDetails`, and existing shared error presentation patterns where applicable.

Add focused pt-BR code mappings for newly operator-visible maintenance errors that are not already localized.

At minimum cover:

```text
BATCH_NOT_EXPIRED
INVALID_STOCK_MOVEMENT_REASON
INVALID_STOCK_ADJUSTMENT
```

Existing relevant mappings include:

```text
BATCH_NOT_FOUND
INSUFFICIENT_STOCK
VALIDATION_ERROR
MALFORMED_REQUEST_BODY
```

The message for `INSUFFICIENT_STOCK` may be refined only if needed to remain accurate for a single selected batch and existing consumers.

Field-level backend validation for `quantity` and `reason` should be surfaced adjacent to the corresponding controls when possible. Unexpected/unhandled error details remain visible through the shared global error presentation.

Authentication/session errors continue to be handled by the #179 centralized 401/session architecture. Do not add maintenance-specific auth logic or request replay.

## Component responsibilities

### `InventoryItemOperationalPage`

Owns:

- authoritative read state already present;
- opening maintenance for a concrete batch;
- backend-confirmed refresh after a completed maintenance operation;
- optional pt-BR success notice.

It must not own stock-maintenance business rules.

### `StockMaintenanceDialog`

Owns:

- selected batch presentation context;
- typed Reactive Form;
- lexical quantity validation;
- destructive confirmation UI;
- duplicate-submit protection;
- invoking one fixed API method according to the selected UI operation;
- mapping backend errors for presentation;
- closing/emitting success only after backend confirmation.

It must not:

- calculate expiration eligibility;
- calculate resulting stock;
- mutate parent batch DTOs;
- perform FEFO;
- expose arbitrary `MovementType` selection.

### `StockMaintenanceApiService`

Owns only typed HTTP calls for the three fixed batch-maintenance routes.

Do not mix presentation state into the service.

## Accessibility and pt-BR copy

All user-facing content must be pt-BR.

At minimum ensure:

- the batch-row maintenance action has an unambiguous accessible name;
- the dialog has a programmatic title;
- operation controls have labels;
- quantity and reason controls have labels and errors;
- confirmation text explains that stock will be reduced and movement history will be created;
- loading/submitting state is exposed through disabled controls and suitable status copy;
- error feedback uses `role="alert"` or the existing shared error component semantics;
- success feedback uses an appropriate polite live region when shown on the parent page;
- keyboard operation and focus behavior use Angular Material dialog defaults rather than custom focus traps.

## Tests

Add focused tests rather than one oversized workflow test.

### API service tests

Cover all three fixed routes:

```text
POST /inventory/batches/{batchId}/adjustments
POST /inventory/batches/{batchId}/losses
POST /inventory/batches/{batchId}/expired-disposals
```

Verify:

- exact path;
- HTTP method;
- request payload;
- exact-decimal string is not converted;
- typed response shape.

Use at least one precision-sensitive value such as:

```text
9999999999999.123456
```

### Dialog/component tests

Cover:

- selected batch context is displayed;
- operation selection is explicit;
- positive adjustment submits the adjustment endpoint without destructive confirmation;
- negative adjustment requires confirmation before HTTP write;
- loss requires confirmation;
- expired disposal requires confirmation;
- exact decimal text reaches the API mock unchanged;
- zero/invalid/over-scale quantity is rejected locally;
- blank/over-length reason is rejected;
- duplicate submit/confirm while request is pending produces exactly one HTTP/service invocation;
- backend `BATCH_NOT_EXPIRED` remains authoritative and displays pt-BR feedback;
- backend `INSUFFICIENT_STOCK`, `BATCH_NOT_FOUND`, validation, and network/server errors produce actionable shared feedback;
- failed write does not emit success or close as successful;
- disposal is not disabled/rejected client-side merely because the batch read model is `AVAILABLE` or has a future/null `expiresAt`.

### Operational-page tests

Cover:

- maintenance is opened from the exact selected batch row;
- successful maintenance refreshes overview and batches;
- successful maintenance refreshes movement history from page 0;
- no refresh occurs before backend-confirmed success;
- existing FEFO withdrawal workflow remains unchanged;
- existing genealogy action remains available.

### Error-localization tests

Cover newly added maintenance error-code mappings.

### Existing broader tests

Update route/workflow mocks only as necessary for the new focused service/dialog. Do not weaken guards, production code, or existing assertions merely to satisfy the new dependency.

## Validation

From `frontend/` run:

```text
pnpm lint
pnpm test
pnpm build
```

Then from repository root:

```text
git diff --check
git diff
git status --short
```

Known unrelated warnings should be reported rather than fixed opportunistically.

## Acceptance criteria mapping

### Concrete batch selection

Satisfied by launching the maintenance dialog from a specific row and passing that backend batch identity/context.

### Signed adjustment

Satisfied only through the existing `/adjustments` endpoint. Do not locally rewrite current balance.

### Loss

Satisfied only through `/losses` delivered by #180.

### Expired disposal

Satisfied only through `/expired-disposals` delivered by #180.

### Exact decimal

Satisfied by string DTOs, text forms, lexical validation, and no JavaScript numeric conversion.

### Mandatory audit reason

Satisfied by typed form validation plus backend validation/error handling.

### Destructive confirmation

Satisfied for loss, disposal, and negative adjustment before the HTTP call.

### Backend disposal authority

Satisfied by never calculating expiration eligibility in Angular and surfacing backend rejection.

### No optimistic stock authority

Satisfied by leaving existing read state unchanged until backend success and then refetching.

### Duplicate submission

Satisfied by local in-flight guard plus disabled submit/confirm controls.

### Refresh

Satisfied by backend refetch of overview, batches, and movement page 0 after success. Separate alerts remain query-on-route-entry and require no new global cache invalidation architecture.

### Actionable pt-BR errors

Satisfied through shared error mapping/localization and field/global presentation.

### FEFO preservation

Satisfied by leaving `FefoWithdrawalPanel` and backend FEFO semantics unchanged.

## Constraints

- Angular 22 and existing Angular Material/CDK only.
- Native `HttpClient`.
- Typed Reactive Forms for the new dialog.
- Signals are appropriate for local component UI state; RxJS is appropriate for HTTP subscription/finalization where useful.
- No NgRx/global store.
- No Axios.
- No new dependency.
- No client-side stock/expiration/FEFO business engine.
- No optimistic authoritative balance mutation.
- No request replay.
- No generic movement editor.

## Out of scope

Do not implement:

- backend changes;
- movement editing/deletion;
- automatic loss/disposal;
- scheduled jobs or notifications;
- production reversal/cancellation;
- FEFO redesign;
- #73 minimum-shelf-life policy;
- inventory administration redesign;
- global alert state/cache infrastructure;
- cross-route global invalidation store;
- unrelated refactors of the existing minimum-stock Signal Forms implementation.

## ADR decision

No ADR is required.

This issue adds a focused frontend workflow on top of established public APIs and existing inventory-page architecture. It does not introduce a new durable application architecture, persistence model, global state model, or cross-module contract.

If implementation appears to require a global store, new cross-feature event bus, new backend contract, or a different inventory authority model, stop and report the structural contradiction before proceeding.

## Final implementation report

Before finishing, report:

1. files created/modified;
2. relevant implementation decisions;
3. tests added/updated;
4. production code changed and why;
5. `pnpm lint`, `pnpm test`, and `pnpm build` results;
6. warnings/non-blocking observations;
7. acceptance criteria not satisfied, if any;
8. `git diff --check` result;
9. `git status --short`.

Do not commit, push, or open a PR from the implementation agent.