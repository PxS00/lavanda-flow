# Issue #224 — Make inventory alerts actionable

## Status

Approved implementation specification for GitHub issue #224.

Branch:

`feature/224/make-inventory-alerts-actionable`

The GitHub issue remains the product source of truth. This document records the implementation decisions required to apply the issue consistently to the current repository after #230.

## Objective

Turn existing inventory alerts into direct, safe operator actions by composing the current receipt, stock workspace, batch maintenance, and item-operation flows.

The implementation must reduce navigation without moving inventory rules into Angular or creating duplicate write APIs.

## Sources of truth

Before implementation, read:

- GitHub issue #224;
- `AGENTS.md`;
- `frontend/AGENTS.md`;
- `docs/architecture/architecture.md`;
- `docs/inventory/movement-history.md`;
- `docs/specs/0230-expose-grouped-stock-workspaces.md`;
- the current alert page, receipt page, operational item page, stock-maintenance dialog, routes, and tests.

Backend rules and contracts remain authoritative.

## Current repository assessment

The repository already provides all backend capabilities required by #224:

- backend-authoritative low-stock alerts;
- backend-authoritative expiration alerts with `inventoryItemId`, `batchId`, expiration status, quantity, and dates;
- `/receipts?inventoryItemId=...` reload-safe receipt preselection;
- `/inventory/items/:inventoryItemId` operational workspace;
- authoritative batch loading for an inventory item;
- audited stock-maintenance operations;
- `EXPIRED_DISPOSAL` as an existing maintenance operation;
- backend validation of expired-disposal eligibility;
- #230 grouped stock workspaces and stable inventory routes.

No backend endpoint, DTO, persistence change, Flyway migration, or new alert algorithm is required.

## Architectural decision

Issue #224 is expected to be frontend-only.

Angular may transport stable identifiers between existing screens. It must not transport or invent authoritative stock state.

Approved handoff inputs are:

- `inventoryItemId`;
- `batchId`;
- a presentation/action hint identifying the existing expired-disposal UI flow.

Do not hand off:

- current quantity as an authoritative value;
- expiration eligibility;
- FEFO allocations;
- derived available stock;
- a serialized batch object;
- hidden in-memory-only state required for reload.

The destination must reload authoritative data through existing APIs before presenting a batch-specific operation.

## Alert actions

### Low-stock alerts

Every existing low-stock alert row must expose:

- `Registrar entrada` as the primary corrective action;
- `Abrir estoque` as the inspection action.

`Registrar entrada` targets:

```text
/receipts?inventoryItemId={inventoryItemId}
```

This reuses the current receipt preselection behavior.

Do not create a second receipt form or API.

Do not calculate whether the row is "zero stock" in Angular to decide which workflow applies. A low-stock alert whose backend-provided available quantity is zero uses the same receipt action.

The existing low-stock backend contract is not expanded to invent a separate zero-stock alert list. Aggregate dashboard zero-stock counts do not contain item identity and therefore must not be turned into a fake item-level preselection flow in this issue.

### Expiring-soon alerts

An `EXPIRING_SOON` row must expose an inspection action such as:

`Abrir lote`

Target:

```text
/inventory/items/{inventoryItemId}?batchId={batchId}#batches
```

The operational item page must use the explicit `batchId` as a presentation/navigation target only.

After authoritative batches load:

- the matching batch row is visibly identifiable;
- the operator can inspect its persisted lot code, current quantity, status, received date, and expiration;
- normal batch maintenance remains available.

No disposal dialog opens automatically for `EXPIRING_SOON`.

### Expired alerts

An `EXPIRED` row must expose:

`Descartar vencido`

Target:

```text
/inventory/items/{inventoryItemId}?batchId={batchId}&maintenance=expired-disposal#batches
```

The query string is a reload-safe UI handoff. It is not business authorization.

The operational page must:

1. load the item and batches through the existing backend APIs;
2. find the exact loaded batch by `batchId`;
3. if found, open the existing stock-maintenance dialog for that batch;
4. initialize the dialog operation as `EXPIRED_DISPOSAL`;
5. preserve the existing quantity, reason, confirmation, error, and backend-validation behavior.

Do not prefill the disposal quantity from alert data.

Do not auto-submit.

Do not bypass confirmation.

Do not decide disposal eligibility from alert metadata or client-side date arithmetic.

If the backend-loaded batch is no longer eligible, the existing backend operation must reject the attempted disposal using the existing error contract.

## Handoff lifecycle

The operational page must treat `maintenance=expired-disposal` as a one-time navigation intent.

Once the matching authoritative batch has been loaded and the dialog has been opened, consume/remove the `maintenance` query parameter with a replace-style navigation while preserving `batchId`.

This prevents:

- reopening the dialog after the batch list refreshes following a successful maintenance operation;
- duplicate dialogs from repeated observable emissions.

Keeping `batchId` allows the page to continue identifying the relevant batch after the action intent is consumed.

A fresh direct navigation/reload with the full URL, including `maintenance=expired-disposal`, must reconstruct the handoff and open the dialog after backend data loads.

## Missing or stale batch handoff

A route/query handoff may become stale between alert generation and navigation.

If the requested `batchId` is absent from the authoritative batch response:

- do not open maintenance;
- do not invent a batch from alert data;
- keep the item workspace usable;
- show a concise pt-BR notice explaining that the indicated batch is no longer available in the current item view and that alerts may be refreshed.

If the batch request itself fails:

- preserve the existing batch error state and retry flow;
- do not open the dialog from stale query parameters.

Unknown `maintenance` values must not trigger a stock operation.

## Stock-maintenance dialog

The existing dialog must remain the single UI for:

- adjustment;
- loss;
- expired disposal.

Allow it to accept an optional initial operation hint.

Requirements:

- existing callers without a hint continue to default to `ADJUSTMENT`;
- the alert handoff may initialize it to `EXPIRED_DISPOSAL`;
- the user can still see the selected operation and normal guidance;
- all existing validation and confirmation behavior remains unchanged;
- backend APIs remain unchanged.

Keep this UI-only hint out of inventory DTOs and backend contracts.

## Operational item batch targeting

The existing item workspace should support a targeted `batchId` without becoming a new batch-detail feature.

The batches section should have a stable fragment target:

```text
#batches
```

When the backend-loaded row matches the requested `batchId`, provide a visible pt-BR indication such as:

`Lote selecionado`

and an appropriate CSS state that remains accessible without relying only on color.

Do not filter other batches out.

Do not change ordering.

Do not infer status.

## Alert-page presentation

Preserve current loading, error, retry, expiration-window, and backend status behavior.

Low-stock table actions:

- `Registrar entrada`;
- `Abrir estoque`.

Expiration table actions:

For `EXPIRED`:

- `Descartar vencido`;
- optional secondary `Abrir lote` is allowed only if it remains concise.

For `EXPIRING_SOON`:

- `Abrir lote`.

Use current backend-provided `status`; do not reclassify by `expiresAt` or `daysUntilExpiration`.

Reuse the shared pt-BR unit label helper so operator-facing unit names do not leak raw values such as `MILLILITER`.

## Dashboard

Existing dashboard metric links for low stock and expiration already lead to `/inventory/alerts` and remain valid.

Do not add a new backend zero-stock alert list or synthetic identity handoff from aggregate dashboard counts.

No dashboard production-code change is required unless a very small accessibility/navigation correction is necessary to preserve the existing behavior.

## Route and state constraints

Use existing routes:

```text
/receipts
/inventory/alerts
/inventory/items/:inventoryItemId
```

Do not add a new route solely for expired disposal.

Use URL query parameters/fragments for reconstructable navigation.

Do not use:

- router navigation extras state as the only source of required identifiers;
- local storage;
- session storage;
- singleton transient state;
- alert DTO objects passed through memory.

## Backend impact

Expected backend production-code changes:

none.

Expected backend test changes:

none.

If implementation discovers that an existing backend contract is insufficient, stop and report the concrete gap rather than silently expanding #224.

## Frontend implementation shape

Expected modified areas:

- `frontend/src/app/features/inventory/pages/inventory-alerts-page/`;
- `frontend/src/app/features/inventory/pages/inventory-item-operational-page/`;
- `frontend/src/app/features/inventory/ui/stock-maintenance-dialog/`;
- focused routing/workflow tests where they provide higher-value end-to-end evidence.

No new frontend feature service is expected.

No new dependency is expected.

## Tests

### Inventory alerts page

Cover:

- low-stock alert renders `Registrar entrada` with exact `/receipts?inventoryItemId=...` target;
- low-stock alert also renders `Abrir estoque`;
- a backend low-stock row whose `availableQuantity` is `0` uses the same receipt action without introducing client-side zero-stock policy;
- `EXPIRED` alert renders exact expired-disposal handoff URL;
- `EXPIRING_SOON` alert renders exact batch inspection URL;
- backend-provided expiration statuses remain the sole classification source;
- raw unit wire values are not presented when a pt-BR label exists;
- loading, empty, error, retry, and expiration-window behavior remain intact.

### Operational item page

Cover:

- `batchId` query identifies/highlights the matching authoritative batch;
- a direct reload with `batchId` works after backend batch loading;
- `batchId + maintenance=expired-disposal` opens the existing dialog only after the matching backend batch loads;
- the dialog receives `EXPIRED_DISPOSAL` as the initial operation;
- the action intent is consumed so subsequent batch refreshes do not reopen it;
- `batchId` remains available for row identification after the maintenance intent is consumed;
- missing target batch produces a notice and no dialog;
- batch-load error produces no dialog and preserves retry;
- unknown maintenance value opens no operation;
- normal manual `Manutenção` continues to open the dialog with its existing default behavior.

### Stock-maintenance dialog

Cover:

- default operation remains `ADJUSTMENT` when no initial operation hint is supplied;
- optional `EXPIRED_DISPOSAL` initial operation is honored;
- existing validators, destructive confirmation, backend error handling, and API selection remain unchanged.

### Operational workflow

Add or adjust a high-value workflow test when practical to prove:

```text
alert -> route handoff -> authoritative batch load -> existing dialog
```

and:

```text
low stock alert -> receipt route -> existing item preselection
```

Do not duplicate every component-level assertion in the application-level test.

## Validation

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

Backend `./mvnw verify` is not required if no backend files change. If backend production or test code changes, run the full backend validation and justify the scope expansion.

## Review checkpoints

Before completion, inspect the complete diff for:

- client-side expiration/disposal eligibility logic;
- client-side FEFO or availability calculations;
- duplicated receipt or disposal APIs;
- hidden router state required for reload;
- stale alert quantities used as authoritative operation inputs;
- auto-submission of destructive operations;
- bypassed confirmation;
- duplicate dialog opening after refresh;
- raw enum/unit wire values in operator-facing copy;
- route/query parameter leakage into unrelated pages;
- unrelated #230 or #225 work;
- new backend code without a demonstrated requirement.

## Out of scope

Preserve issue #224 exclusions:

- new alert algorithms or thresholds;
- new zero-stock alert backend contract;
- minimum shelf-life policy #73;
- push/email/WhatsApp/background notifications;
- automatic purchasing or replenishment;
- dashboard analytics;
- new receipt API;
- new disposal API;
- new stock-maintenance operation;
- new batch-detail route;
- backend inventory-rule changes.

## Acceptance mapping

- Low/zero available rows in the existing low-stock alert contract get direct receipt preselection.
- Expired alerts target the concrete batch and existing expired-disposal dialog.
- Expiring-soon alerts target the concrete batch for inspection.
- Existing maintenance confirmation/validation/error behavior remains intact.
- Backend remains authoritative for status and eligibility.
- URLs contain stable identifiers, so direct reload is reconstructable.
- No duplicate APIs or hidden business state are introduced.
- UI remains pt-BR and uses the existing responsive table/workspace patterns.
