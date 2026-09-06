# Issue #137 — Render operational inventory dashboard

## Objective

Replace the existing `/dashboard` placeholder with the V1 operational inventory dashboard backed exclusively by the backend-authoritative summary introduced by #135.

The frontend renders server-provided counters and context. It must not fetch raw inventory items or batches to derive availability, low-stock, out-of-stock, or expiration status.

## Source of truth

Apply, in order:

1. GitHub issue #137;
2. `AGENTS.md`;
3. `frontend/AGENTS.md`;
4. the existing `/dashboard` route and application shell;
5. the #135 HTTP contract at `GET /api/v1/inventory/dashboard`;
6. existing shared loading/error primitives and stable catalog/inventory routes;
7. this specification for the concrete frontend implementation decisions required by #137.

Do not change backend inventory semantics or introduce frontend equivalents of backend business rules.

## Existing backend contract

Consume exactly:

```http
GET /api/v1/inventory/dashboard
```

Response fields:

```text
asOfDate
expirationWindowDays
activeItemCount
lowStockItemCount
outOfStockItemCount
expiringSoonBatchCount
expiredBatchCount
```

Frontend code keeps these wire names unchanged.

`asOfDate` and `expirationWindowDays` are backend context values. Angular may format them for display but must not use them to recompute any counter.

## Frontend ownership and structure

Keep the existing capability-oriented `features/dashboard` feature.

Add the smallest dashboard-specific data-access surface required by the page:

- one typed DTO matching the seven backend fields;
- one `HttpClient` service that performs only `GET /api/v1/inventory/dashboard`;
- page state and presentation under the existing dashboard feature.

Do not place HTTP calls directly in `DashboardPage`.

Do not introduce a generic analytics/dashboard framework, global store, NgRx, new dependency, chart library, or cross-feature state abstraction.

Signals are appropriate for local page state. RxJS may be used for request/refresh composition following existing project patterns.

## Page state

The dashboard has three request states:

```text
loading
loaded
error
```

Requirements:

- initial navigation to `/dashboard` immediately loads the summary;
- loading is explicit and uses the existing shared loading primitive where it fits;
- transport/backend failures are converted through the existing `mapHttpError` / `UiError` path and rendered with the shared error state;
- an explicit pt-BR refresh action requests the same summary again and performs no mutation;
- repeated refreshes must not create stale-response races; use the existing switch-to-latest pattern or an equivalent simple cancellation-safe composition;
- no optimistic or locally authoritative dashboard/stock state is introduced.

The page does not silently retain an old response as authoritative after a failed refresh unless the existing project UI pattern explicitly supports a clearly marked stale state. The smallest implementation may transition to loading and then loaded/error.

## Loaded presentation

Always render the five operational metrics when a summary is successfully loaded, including when their values are zero:

| Backend field | Operator-facing label |
| --- | --- |
| `activeItemCount` | `Itens ativos` |
| `lowStockItemCount` | `Estoque baixo` |
| `outOfStockItemCount` | `Sem estoque` |
| `expiringSoonBatchCount` | `Lotes próximos do vencimento` |
| `expiredBatchCount` | `Lotes vencidos` |

Zero is a valid operational value. A zero metric must remain visible as `0`; do not hide the card, treat zero as missing data, or render an error.

If all counters are zero, the five metrics still remain visible. A supplementary neutral empty/all-clear message is optional only if it does not replace or hide the counters.

Also show concise backend-provided context in pt-BR:

- format `asOfDate` using the existing frontend local-date formatting utility;
- expose `expirationWindowDays` as the configured upcoming-expiration window so the meaning of `Lotes próximos do vencimento` is clear;
- do not calculate the future cutoff date in Angular.

Do not add charts, trends, forecasts, recommendations, notification controls, category breakdowns, or historical analytics.

## Navigation

Use only routes that already exist on `develop`.

Supported navigation:

- `Itens ativos` -> `/catalog`;
- `Estoque baixo` -> `/inventory/alerts`;
- `Lotes próximos do vencimento` -> `/inventory/alerts`;
- `Lotes vencidos` -> `/inventory/alerts`.

Do **not** invent query parameters, filters, fragments, or a new route to represent a metric.

`Sem estoque` has no existing dedicated route that accurately represents all backend out-of-stock items, so it remains a non-link metric in this issue rather than linking to a misleading destination.

Use semantic links/buttons and accessible pt-BR names. Navigation must remain usable by keyboard.

## Layout and accessibility

Use the current Angular Material application style rather than creating a new visual system.

Requirements:

- compact metric cards/tiles with clear label and numeric value hierarchy;
- responsive layout that naturally wraps/reflows on smaller screens without horizontal overflow;
- semantic page heading;
- accessible link/button names that make the destination/action understandable;
- refresh state is perceivable without relying on color alone;
- shared loading/error states retain their existing accessibility behavior;
- all operator-visible text and accessibility copy are pt-BR.

A simple CSS grid/flex layout and existing Angular Material components are sufficient.

## Error and zero-state semantics

Do not conflate these cases:

- request failed -> error state;
- request pending -> loading state;
- request succeeded with zero values -> loaded operational state with visible zero metrics.

No successful backend response is considered empty merely because one or all counters are zero.

## API and business-rule constraints

The dashboard must make exactly the summary request needed for this feature.

It must not request raw catalog/batch/alert collections to derive any dashboard counter.

Frontend must not implement or infer:

- available-stock eligibility;
- FEFO;
- minimum-stock comparisons;
- out-of-stock policy;
- `expiresAt <= today`;
- expiration-window membership.

Those semantics remain entirely backend-authoritative.

The frontend may only map DTO fields to presentation labels, formatting, links, and visual states.

## Tests

### Data access

Cover the dashboard API service:

- exact request to `/api/v1/inventory/dashboard` through the existing `API_BASE_URL` convention;
- GET method;
- typed response with all seven wire fields preserved.

### Dashboard page

Cover at minimum:

1. initial loading and successful rendering;
2. all five counters rendered directly from the response;
3. `asOfDate` and `expirationWindowDays` context rendered in pt-BR;
4. all-zero response keeps all five metrics visible as zero and does not render an error;
5. backend/transport error uses the existing mapped error/shared error-state contract;
6. explicit refresh causes a new summary request and renders the replacement response;
7. refresh composition does not allow an older response to overwrite the newest request;
8. supported metric links use only `/catalog` and `/inventory/alerts` as specified;
9. `Sem estoque` does not invent a destination;
10. existing `/dashboard` route and application-shell behavior remain intact.

Do not add brittle tests based on CSS implementation details when accessible text/roles/behavior can verify the requirement.

## Scope constraints

No backend changes are required by #137.

Do not change:

- backend dashboard semantics or API wire values;
- inventory alert behavior;
- catalog behavior;
- application-shell navigation beyond what is concretely required to render this existing route;
- unrelated feature code.

Do not introduce:

- forms;
- Signal Forms;
- NgRx;
- charting libraries;
- another HTTP client;
- global dashboard state;
- new API routes;
- new dependencies.

## Acceptance criteria

- [ ] `/dashboard` requests only the backend summary from #135.
- [ ] The exact seven-field dashboard DTO is represented in frontend data access.
- [ ] The five V1 counters render directly and accurately from the backend response.
- [ ] Zero values remain visible valid operational values.
- [ ] Loading and mapped error states use existing shared primitives/contracts.
- [ ] Explicit refresh reloads the GET summary without backend mutation or stale-response races.
- [ ] `asOfDate` and `expirationWindowDays` are displayed as backend-provided context without frontend cutoff calculation.
- [ ] Supported metric navigation uses only existing `/catalog` and `/inventory/alerts` routes.
- [ ] No misleading/speculative destination is added for `Sem estoque`.
- [ ] Responsive and accessible layout is usable on desktop and smaller screens.
- [ ] All operator-facing and accessibility copy is pt-BR.
- [ ] No raw inventory data is fetched to recompute dashboard rules.
- [ ] Existing route/application-shell behavior remains intact.
- [ ] Tests cover success, zero values, error, refresh/latest-response behavior, and supported navigation.
- [ ] `pnpm lint`, `pnpm test`, and `pnpm build` succeed.

## Out of scope

- charts and trends;
- historical analytics;
- forecasting or purchasing recommendations;
- notifications;
- CSV migration/import UI;
- backend dashboard changes;
- new alert filters/routes solely for dashboard navigation;
- minimum-shelf-life policy from #73;
- authentication or deployment.

## Final validation

From `frontend/`:

```bash
pnpm lint
pnpm test
pnpm build
```

Then:

1. review the complete `git diff` for frontend business-rule duplication and unrelated changes;
2. run `git diff --check`;
3. run `git status --short`.

Do not force `pnpm test -- --run`.