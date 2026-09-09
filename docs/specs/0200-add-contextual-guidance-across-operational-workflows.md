# Issue #200 — Contextual guidance across operational workflows

## Status

Approved implementation specification for issue #200.

The GitHub issue remains the source of truth for Objective, Context, Scope, Acceptance Criteria, Constraints, and Out of Scope. This document fixes the implementation decisions needed to execute that issue consistently against the current Angular application.

## Branch and baseline

- Branch: `feature/200/add-contextual-guidance-across-operational-workflows`
- Base: `develop`
- Baseline commit: `ef7586b12640da64c087967db559b2f2bf6e476f`
- Visual foundation dependency: #199 is merged.

## Objective

Add concise pt-BR guidance where the operator currently needs domain or workflow context, while keeping the application fast to scan during routine use and preserving backend authority for inventory, expiration, FEFO, production, availability, and audit semantics.

This issue changes presentation copy and existing frontend composition only. It does not add new workflows, routes, API contracts, business rules, or backend behavior.

## Current-state findings

The current frontend already contains useful guidance in several places and should not be rewritten indiscriminately:

- dashboard already explains that its values are current stock indicators and exposes the backend-provided reference date/window;
- the item operational page already distinguishes physical quantity from available stock and states that availability follows system rules;
- FEFO withdrawal already warns before confirmation that eligible lots are selected automatically and multiple lots may be used;
- production registration already states that formula, stock, expiration, eligibility, and final lot values are server-authoritative;
- loading, error, and empty states are already explicit shared primitives;
- catalog and supplier lists already distinguish filtered empty results from the initial empty collection;
- production registration already provides an adjacent `Cadastrar fórmula` action when no formula exists.

The implementation should preserve those correct concepts, simplify technical wording where appropriate, and fill the real guidance gaps instead of adding repetitive text to every page.

## Implementation decisions

### 1. Guidance hierarchy

Use the smallest persistent guidance surface that answers the operator's question:

1. **Page subtitle** — one short sentence for the page purpose when not self-evident.
2. **Section/context note** — short persistent text near a consequential workflow or domain distinction.
3. **`mat-hint` / field hint** — input-specific guidance required to enter the correct kind of data.
4. **Confirmation copy** — consequence and audit semantics immediately before a stock-changing action.
5. **Empty-state message / adjacent action** — explain the practical next step when data is absent.
6. **Tooltip** — supplementary only; never the sole source of workflow-critical information.

Do not introduce a help framework, copy registry, tour system, global guidance service, or new design-system abstraction.

### 2. Terminology

User-facing copy remains pt-BR and should use operator language rather than implementation language.

Preferred terms:

- `sistema` instead of `backend` in operator-facing explanations;
- `entrada de estoque`, `retirada`, `correção de estoque`, `perda física`, `descarte por vencimento` for distinct inventory actions;
- `histórico de movimentações` / `movimentação auditável` for append-only stock history;
- `estoque disponível` only for the value returned by the existing backend contract;
- `lotes selecionados automaticamente por validade (FEFO)` when explaining FEFO behavior.

Do not rename routes, DTO fields, enum/wire values, API contracts, or engineering identifiers.

### 3. Backend-authoritative language boundary

Guidance may explain what the system does, but it must not independently decide whether an operation is valid.

Frontend copy may state, for example:

- the system chooses eligible withdrawal lots by FEFO;
- corrections create new movements rather than rewriting history;
- the system validates stock, expiration, eligibility, formula consistency, and production registration;
- available stock is the value confirmed by the system.

Frontend code must not add a second rule engine to derive:

- FEFO allocation;
- expiration eligibility;
- available stock;
- loss/disposal eligibility;
- formula scaling or production eligibility;
- generated lot sequences;
- stock balances.

A backend rejection remains authoritative even if a hint describes the expected operation.

### 4. Dashboard

Keep the existing dashboard metrics, links, data requests, and semantics unchanged.

The existing `Painel operacional` subtitle and backend-provided date/window context already satisfy the page-orientation need. Only make copy changes here if they remove technical ambiguity; do not add tutorial content or new metrics.

### 5. Catalog list and search

Preserve the current header, filters, pagination, and `Cadastrar item` action.

For empty results:

- filtered empty state must continue to mean `no match`, not system failure;
- initial empty state should clearly point the operator toward the existing `Cadastrar item` action;
- do not invent automatic navigation or a new empty-state route.

The current shared `EmptyState` API (`title` + `message`) is sufficient unless implementation proves otherwise. Do not expand it merely to centralize buttons; an existing page-level or adjacent action is acceptable.

### 6. Item registration and detail

Keep registration and detail workflows unchanged.

Use concise guidance only for operationally ambiguous fields. Do not add long descriptions to obvious fields such as name or category.

Issue #201 owns the operational visibility and explanatory treatment of `essenceReference` and `productionTypeCode`. #200 must not expand those reference-code semantics, add code badges, invent a taxonomy, or move that work forward accidentally.

Existing validation hints may remain when they communicate input shape rather than business authority.

### 7. Item operational workspace

This page is a primary guidance target.

#### Overview

Preserve the current distinction between:

- physical quantity; and
- backend-provided available stock.

Keep the note concise and operator-oriented. Prefer `sistema` rather than implementation terms.

#### Lots

When no lots exist, explain that stock enters through the existing stock-receipt workflow rather than direct balance editing. If an action is added, it must use the existing `/receipts` route and should preserve the selected `inventoryItemId` context where practical.

Do not imply that the operator may directly create or edit a balance.

#### Minimum stock

Keep current backend validation and form behavior. Hints may explain that this threshold is used for stock monitoring, but must not reimplement low-stock calculation or alert logic in the frontend.

#### Movement history

When empty, explain that the history appears after audited stock operations.

Add persistent concise copy establishing the audit model where useful:

- existing movements are not edited to correct stock;
- corrections create new movements.

Do not add edit/delete history controls.

### 8. FEFO withdrawal

Keep withdrawal item-scoped and preserve the existing API/request behavior.

Add or refine persistent guidance before the action so the operator does not need to infer FEFO behavior from the confirmation step alone:

- the operator informs the desired withdrawal quantity;
- the system selects eligible lots automatically by validity/FEFO;
- the operator does not manually override the allocation in this workflow;
- more than one lot may be consumed when required.

The confirmation remains the place to state that stock will be consumed.

Do not calculate lot allocation in the frontend or promise that a withdrawal will succeed before the backend confirms it.

### 9. Stock maintenance dialog

Make the three operations clearly distinguishable before submission:

- **Correção de estoque** — records an audited correction; it does not rewrite earlier history.
- **Perda física** — records stock physically lost as a new audited movement.
- **Descarte por vencimento** — records disposal for expiration; the system remains authoritative for whether the selected lot is eligible.

Guidance should be persistent in the dialog, not tooltip-only.

For quantity guidance:

- explain signed quantity only for `ADJUSTMENT` if the existing form contract already supports positive and negative values;
- do not infer or enforce eligibility rules for LOSS or EXPIRED_DISPOSAL beyond existing frontend contract validation;
- preserve backend-confirmed submission and the existing confirmation step for stock reductions.

Confirmation copy must continue to state that a stock reduction creates a new auditable movement.

### 10. Stock receipt

Preserve the current one-operation creation of a lot and its initial receipt movement.

Refine helper text where useful so the operator understands:

- the selected item is the item receiving stock;
- supplier association is optional/required only according to the existing contract, not a new frontend rule;
- lot code identifies the received batch when available;
- expiration is batch metadata and system date rules remain authoritative;
- reason/notes are audit context, not a mechanism for changing stock rules.

Do not add frontend date eligibility logic.

Success copy should remain clear that both the lot and initial movement were registered.

### 11. Production formulas

Clarify that a production formula defines reference output/ingredient quantities used by the production workflow; creating or editing a formula does not itself consume or create stock.

The existing `Registrar produção` action remains the workflow that records an actual production execution.

When no formulas exist, continue pointing to the existing `/production/formulas/new` action. Do not add a new setup wizard.

Do not implement formula scaling in the browser.

### 12. Production registration

Preserve all existing production requests, exact-batch selection, review state, refresh behavior, and server authority.

The page already contains strong correctness-oriented guidance. Refine it rather than duplicating it:

- replace operator-visible implementation jargon such as `backend` with `sistema` or `servidor` when a clearer term exists;
- keep the warning that review does not guarantee acceptance;
- keep generated lot sequencing server-authoritative;
- keep formula/reference quantities explicitly non-binding in the UI;
- keep the post-success refresh failure warning that production must not be submitted twice.

Do not add automatic lot allocation, FEFO allocation, formula scaling, eligibility decisions, or optimistic production state.

### 13. Genealogy / traceability

Where the existing genealogy page needs orientation, explain concisely that it traces the production ancestry of a concrete batch through recorded source/output relationships.

Do not create a new production-history route or general-purpose history feature. Existing routes remain unchanged.

### 14. Suppliers

Supplier list/registration/detail pages are already comparatively self-explanatory.

Keep guidance minimal:

- preserve the existing explanation that suppliers are used in stock receipts;
- filtered empty results remain distinct from initial empty state;
- initial empty state may point toward the existing `/suppliers/new` action.

Do not add purchasing, order, pricing, or procurement semantics.

### 15. Shared errors

`localize-ui-error.ts` and the existing error-state infrastructure remain the source of shared HTTP/security/business error localization.

Do not duplicate those mappings in individual pages merely to make copy more contextual. Page-specific surrounding guidance may explain what the operator should do next without replacing the shared error message.

### 16. Tooltips

No tooltip is mandatory for this issue.

Introduce `MatTooltip` only if a touched compact/non-obvious control has a concrete supplementary-help need. If used:

- it must remain keyboard accessible;
- it must not contain required safety/workflow instructions;
- touch users must not depend on hover.

Do not add an icon library or new dependency for tooltip affordances.

## Expected implementation footprint

The exact diff should remain driven by actual guidance gaps, but likely touched areas include existing templates/tests under:

- `features/catalog`;
- `features/inventory`;
- `features/receipts`;
- `features/production`;
- `features/suppliers` only where useful;
- shared empty/error state code only if a demonstrated repeated need requires it.

Do not change:

- backend Java;
- Flyway migrations;
- database schema;
- Compose/runtime configuration;
- auth/session/XSRF behavior;
- route contracts;
- API/DTO/wire contracts;
- #199 branding architecture;
- #201 reference-code visibility;
- #202 collapsible-navigation behavior.

## Testing strategy

Update existing tests where visible text changes and add focused tests for guidance that protects important operator semantics.

At minimum, cover where applicable:

- FEFO guidance is persistently visible before confirmation and does not expose manual-allocation controls;
- stock-maintenance copy clearly distinguishes correction, loss, and expired disposal;
- audit guidance states that corrections create new movements rather than rewriting history;
- no-lot/no-history/no-formula states provide the intended practical next step while remaining distinct from errors;
- production review still communicates server authority and does not alter request payloads;
- filtered empty catalog/supplier results remain `no match` states rather than errors;
- existing routes/actions used by guidance remain unchanged.

Prefer assertions on meaningful semantics/actions over brittle full-paragraph snapshots or exact CSS details.

Do not weaken existing functional tests to accommodate copy changes.

## Validation

Run from `frontend/`:

```bash
pnpm lint
pnpm test
pnpm build
```

Then from the repository root or the current working directory as appropriate:

```bash
git diff --check
git diff
git status --short
```

Record the existing bundle-budget warning as non-blocking if it remains materially unchanged.

## Completion report

The implementation report must include:

1. files created/modified;
2. guidance/copy decisions by workflow;
3. confirmation that FEFO/expiration/availability/production rules remain backend-authoritative;
4. confirmation that #201 reference-code scope and #202 navigation scope were not implemented;
5. tests added/updated;
6. production code changed and why;
7. `pnpm lint`, `pnpm test`, and `pnpm build` results;
8. known non-blocking warnings;
9. acceptance criteria not yet satisfied, if any;
10. `git diff --check` result;
11. `git status --short`.
