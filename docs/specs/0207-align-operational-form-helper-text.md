# Issue #207 — Align operational form helper text

## Status

Approved implementation specification for issue #207.

The GitHub issue remains the source of truth for objective, scope, acceptance criteria, constraints, and out-of-scope behavior. This specification fixes the concrete frontend implementation decisions needed to improve helper-text alignment without changing form semantics, validation, request payloads, or backend-authoritative behavior.

## Objective

Refine the visual rhythm of operational form helper text, starting with the stock-receipt workflow observed during manual #202 validation, so helper/subtext remains clearly associated with its control and paired fields read as one deliberate grid on supported notebook and tablet layouts.

## Source of truth

Apply, in order:

1. GitHub issue #207;
2. root `AGENTS.md`;
3. `frontend/AGENTS.md`;
4. `docs/specs/0199-establish-branded-operational-ui-foundation.md`;
5. `docs/specs/0200-add-contextual-guidance-across-operational-workflows.md`;
6. current `develop` frontend implementation;
7. existing frontend tests for touched components.

Do not turn this presentation refinement into a form-system redesign. If a requested visual change would require new validation rules, business logic, API changes, or a new dependency, report that as out of scope.

## Branch and base

Branch:

```text
refactor/207/align-operational-form-helper-text
```

The branch starts from `develop` after #202 at:

```text
441e0345c20eb4a5c1f3e0a7964bbc331381d846
```

## Repository observations

The stock-receipt form is the original concrete defect surface. Manual validation of the
corrected receipt fields demonstrated the same Material subscript inset in exactly three
additional operational controls: the inventory-item selector search field, the supplier
selector search field, and the inventory-alerts expiration-window field.

Current receipt structure:

- `.fields-grid` uses two equal columns on notebook widths and collapses to one column at `max-width: 1024px`;
- quantity, lot code, received date, expiration date, and reason use Angular Material `mat-form-field` controls;
- quantity, lot code, expiration date, and reason have informational helper copy;
- `receivedAt` intentionally has no helper copy and must not receive invented copy in this issue;
- conditional backend field-error paragraphs are owned by their corresponding `.receipt-field`, so an error cannot become an independent grid item or disturb field pairing/order;
- `reason` spans both columns;
- the receipt fields now render informational helper copy as application-owned text outside
  the Material subscript layout, preserving their outer-outline alignment and accessible
  descriptions.

A repository audit also found helper text in catalog, suppliers, inventory, receipt selectors, production formula setup, and production registration. The manual validation above demonstrates the same defect only for the three named selector/alert controls. The other surfaces use different layouts: single-column forms, selector-owned form fields, three-column ingredient/allocation rows, or custom contextual copy. No global `mat-hint` override or shared form abstraction is justified in #207.

Therefore the approved implementation remains component-local: receipt fields plus the three
manually demonstrated controls. Other forms remain inspection evidence only.

## Implementation strategy

### Align informational helpers to the outer control edge

For the receipt fields and the three demonstrated controls, render informational helper copy
as an application-owned paragraph immediately after the outlined `mat-form-field`. Use a
stable helper ID and associate it with the relevant input through `aria-describedby`.
Use the public `subscriptSizing="dynamic"` input after removing `mat-hint` so Material does
not reserve an empty subscript row. Keep `mat-error` inside the form field and hide the
external helper only while an existing client validation error is displayed.

Do not:

- duplicate helper text inside and outside `mat-form-field`;
- add helper copy for `receivedAt` merely to fill visual space;
- target `.mat-mdc-*`, MDC internals, private Material classes, or `::ng-deep`;
- counteract Material's own subscript inset with arbitrary negative margins.

The target horizontal axis is the visible outer edge of the outlined control, not the Material
subscript/content axis. Use receipt-local or component-local `.operational-field-helper` styles
with the existing body-small and surface-variant tokens; do not add global CSS.

### Group each receipt field as one grid cell

Introduce a small application-owned wrapper around each receipt field and its external backend error, conceptually:

```html
<div class="receipt-field">
  <mat-form-field>...</mat-form-field>
  @if (backendFieldError(...)) {
    <p class="field-error" ...>...</p>
  }
</div>
```

The wrapper is presentation-only. It must not alter form binding, validation, IDs, `aria-describedby`, error messages, or request behavior.

Use one wrapper per field:

- quantity;
- lot code;
- received date;
- expiration date;
- reason.

The reason wrapper spans the full grid width.

This fixes the current structural weakness where a conditional backend error can occupy its own grid cell and break pair alignment.

### Receipt field grid

Keep the existing responsive information architecture:

Notebook / supported wide layout:

```text
Quantity        | Lot code
Received date   | Expiration date
Reason (full width)
```

Tablet/narrow layout:

```text
Quantity
Lot code
Received date
Expiration date
Reason
```

Use application-owned CSS only.

Recommended structure:

- `.fields-grid`: two columns, `align-items: start`, deliberate row/column gaps from existing Lavanda Flow spacing tokens;
- `.receipt-field`: grid or block container with `min-width: 0` and top-aligned content;
- `.receipt-field--full`: `grid-column: 1 / -1`;
- collapse `.fields-grid` to one column at the existing responsive breakpoint unless manual validation shows a nearby existing breakpoint is necessary;
- when collapsed, full-width wrappers naturally become one column without special positioning hacks.

Do not introduce per-field margin offsets.

### Helper typography and rhythm

The receipt helper text should have a consistent presentation across quantity, lot code, expiration date, and reason:

- start aligned;
- consistent body-small/label-scale typography appropriate for helper text;
- muted Material/Lavanda Flow surface-variant color;
- consistent line height;
- consistent relationship to the field above;
- natural wrapping without clipping;
- no forced single-line truncation;
- no excessive reserved whitespace.

Use application-owned helper spacing and styles for the demonstrated controls. Do not style
private Material subscript wrappers.

Use existing tokens such as `var(--mat-sys-body-small)`, `var(--mat-sys-on-surface-variant)`, and `--lf-space-*` where appropriate. Do not add raw brand hex values.

### Paired-field vertical rhythm

When helper text differs in length or wraps, the grid row must size to the tallest field cell so the next row starts on one shared horizontal baseline.

Do not fake balance by:

- hard-coding different min-heights per field;
- inserting blank helper text;
- adding invisible placeholder copy;
- moving helper copy outside its field solely to equalize heights.

The field wrapper/grid structure should provide the balance naturally.

### Backend field errors

Preserve all existing backend error IDs and `aria-describedby` relationships.

Backend errors must remain inside the same `receipt-field` wrapper as their associated Material field so:

- the error cannot become another column in `.fields-grid`;
- an error does not reorder later controls;
- each message remains visually and semantically associated with its field;
- submitting the same request produces the same validation/API behavior as before.

Do not merge backend errors into Material `mat-error` unless that would preserve every existing semantic and behavior contract and is demonstrably simpler. The default approved implementation is to keep the existing external backend error paragraphs and only group them structurally.

### Demonstrated selector and alert controls

Apply the same local external-helper pattern only to these controls:

- `InventoryItemSelector`: `Os resultados são limitados a 10 itens ativos`;
- `SupplierSelector`: `Deixe sem seleção quando a entrada não tiver fornecedor`;
- `InventoryAlertsPage`: `Use 0 para mostrar somente lotes vencidos.`

Do not change selector-level descriptive copy, empty states, alert headings/status text, or any
other `mat-hint` occurrence. Each component owns its local presentation style because view
encapsulation makes a global helper rule unnecessary.

## Expected implementation footprint

Expected files:

```text
frontend/src/app/features/receipts/pages/stock-receipt-page/stock-receipt-page.html
frontend/src/app/features/receipts/pages/stock-receipt-page/stock-receipt-page.scss
frontend/src/app/features/receipts/pages/stock-receipt-page/stock-receipt-page.spec.ts   # only if structural assertions are useful
frontend/src/app/features/catalog/ui/inventory-item-selector/inventory-item-selector.{html,scss,spec.ts}
frontend/src/app/features/receipts/ui/supplier-selector/supplier-selector.{html,scss,spec.ts}
frontend/src/app/features/inventory/pages/inventory-alerts-page/inventory-alerts-page.{html,scss,spec.ts}
```

Do not edit `stock-receipt-page.ts` unless a real blocker is discovered; this issue should require no production TypeScript behavior change.

Do not edit global `styles.scss`, shared components, catalog selectors, production forms, backend code, routes, APIs, DTOs, auth/session code, or dependencies without first identifying a concrete identical defect that makes a local solution unsafe.

## Behavior that must not change

Preserve exactly:

- `RegisterStockReceiptDto` request mapping;
- exact decimal-string handling;
- item/supplier selection behavior;
- optional supplier behavior;
- lot-code semantics;
- received/expiration date semantics;
- no frontend date-order business rule;
- reason semantics;
- client validation messages;
- backend field error messages and associations;
- submission locking;
- success state and links;
- #200 contextual guidance copy;
- #201 item-reference behavior inherited through the selector;
- #202 application-shell behavior.

No backend-authoritative inventory logic may move into frontend presentation code.

## Testing strategy

Preserve every existing `StockReceiptPage` behavior test.

Because this issue changes markup structure rather than domain behavior, add only focused structural coverage if useful. Appropriate assertions include:

- five receipt field wrappers exist for quantity, lot code, received date, expiration date, and reason;
- each wrapper owns exactly one associated `mat-form-field`;
- the reason wrapper has the full-width class;
- existing helper copy remains unchanged;
- external helper IDs and `aria-describedby` associations remain stable;
- `receivedAt` does not receive invented helper copy;
- when a backend field error is rendered, it remains inside the same wrapper as the corresponding form field and does not become a sibling grid cell.

Do not assert exact pixel positions, CSS computed widths, internal Material classes, or private DOM structure.

Existing request/payload and validation tests remain authoritative regression coverage.

## Manual visual validation

Manual browser validation is required because the defect is visual.

At a supported notebook width, verify:

- quantity and lot-code controls align as one row;
- their helper text begins at the same outer axis as the visible field outline;
- helper typography, line height, and spacing look consistent;
- received-date and expiration-date controls align as one row;
- the absence of helper copy on received date does not make the next row visually unstable;
- reason helper text follows the same visual treatment;
- long helper copy can wrap without overlapping the next row;
- backend field errors, when simulated through the existing UI/test path where practical, remain under the correct field and do not move another field into the wrong column.

At the supported tablet/narrow layout, verify:

- fields collapse to one column cleanly;
- helper text remains associated with the correct field;
- wrapped helper text is readable;
- no clipping or excessive whitespace appears.

## Validation

Required:

```text
cd frontend
pnpm lint
pnpm test
pnpm build
```

Also review:

```text
git diff
git diff --check
git status --short
```

The known intermittent five-second timeout in `app.operational-workflow.spec.ts` may be recorded if observed, but the final complete test run should be green before commit/recommendation when possible. Do not alter unrelated tests merely to make #207 pass.

The existing initial bundle-budget warning may remain non-blocking if materially unchanged.

## Acceptance mapping

#207 is complete when:

- receipt helper text is visually consistent and clearly associated with its field;
- paired receipt fields keep a coherent grid rhythm when helper lengths differ;
- backend field errors cannot become independent receipt-grid columns;
- existing copy/validation/payload semantics remain unchanged;
- no helper text is invented for `receivedAt`;
- no private Angular Material selector or `::ng-deep` is used;
- no global form-system abstraction is introduced without demonstrated need;
- the three manually demonstrated selector/alert helpers align to the outer control edge and
  remain programmatically associated with their inputs;
- no backend/API/DTO/route/auth/dependency change is introduced;
- notebook and tablet manual visual checks pass;
- `pnpm lint`, `pnpm test`, and `pnpm build` pass.

## Out of scope

Preserve the issue boundaries:

- rewriting helper copy for UX/content reasons unrelated to alignment;
- changing validation or business rules;
- adding new hints merely for visual symmetry;
- new form components or a general form-layout framework;
- global Angular Material form-field overrides without demonstrated cross-app need;
- navigation changes from #202;
- backend/API/schema/runtime changes;
- bundle optimization;
- broader visual redesign.
