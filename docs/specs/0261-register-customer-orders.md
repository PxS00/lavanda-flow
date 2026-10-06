# Issue #261 — Register customer orders

## Source of truth and status

[Issue #261](https://github.com/PxS00/lavanda-flow/issues/261), refined by
[spec #259](0259-define-v0.8-commercial-scope.md), the merged
[customer slice #260](0260-manage-customer-contacts.md), and
[ADR 0012](../architecture/decisions/0012-define-v0.8-commercial-boundaries.md), owns this draft-only slice.
Implemented on `feature/261/register-customer-orders` from `develop` commit
`7d3abad` (merged #260). No commits, pushes, PRs, dependencies or release metadata changes are included.

## Customer optionality and snapshot wording resolution

Issue #261 explicitly accepts an **optional customer association**. The #259 data-model phrase
“contains customer UUID” does not specify NOT NULL, and ADR 0012 requires public lookup validation
without requiring every draft to associate a customer. Therefore `sales_order.customer_id` is nullable,
POST/PUT accept omitted or null `customerId`, and an omitted/null value on full replacement clears the
association. A supplied UUID must resolve to an active contact on each save. This resolves the wording
without a new architectural boundary. A customer FK preserves a supplied reference; no JPA customer
relationship crosses modules.

The #261 historical-line criterion is read together with #259's explicit confirmation-time snapshots
and the user's refinement: stable item/customer references, line identities, quantity, entered price,
rounded amount and total are persisted on drafts. Later catalog/customer edits do not change those
values. Names and units displayed on drafts are **live lookup labels**, not immutable confirmed history.
Customer/product display snapshots and stock allocation history are deferred to #262.

## Ownership and invariants

One cohesive `sales` module owns the order aggregate and statuses `DRAFT`, `CONFIRMED`, `CANCELLED`.
This slice creates/reads drafts only and exposes no state transition, confirm, cancel or delete endpoint.
Implemented dependencies are public `customers`, public `catalog`, and `shared::error`; inventory is
not needed until confirmation. Existing modules acquire no sales dependency.

`OrderManagement` owns write transactions, application-Clock timestamps, reference validation and
exact total calculation. Catalog validation and live labels use the existing batched public lookup. Timestamps use PostgreSQL-compatible microsecond precision. Edits lock the order row. Both creates and full replacements require one or
more lines, one line per stable catalog item, positive quantities, and explicit nonnegative unit prices.
Only active `FINISHED_PRODUCT` items with `MILLILITER` or `UNIT` are accepted, using
`InventoryItemDetailsLookup`. `CustomerLookup` returns both active/inactive contacts and empty for
missing IDs; sales rejects missing or inactive supplied contacts. No lookup exposes persistence types.
No availability query, inventory call, stock write, reservation, unit conversion, FEFO or batch selection
occurs. A save makes no stock availability guarantee.

Order UUID and creation timestamp never change. Each retained item keeps its original line UUID even
when the request omits its line ID. New lines omit/use null ID and receive a server UUID. A supplied
line ID must identify the same item on this existing order; foreign IDs, ID reassignment and supplied
IDs on creation are rejected. Full replacement removes omitted lines and retains request display order.

## Exact arithmetic

Currency is fixed to BRL. Quantities accept at most 13 integer and 6 fractional digits and persist as
`NUMERIC(19,6)`; unit prices accept at most 15 integer and 4 fractional digits and persist as
`NUMERIC(19,4)`. No quantity rounding or unit substitution exists. Zero price is valid. Missing values,
negative prices, nonpositive quantities, excessive precision and numeric overflow are rejected before
persistence. Exponent inputs are bounded before scale normalization as well.

Multiply quantity and price using `BigDecimal`, round **each** line with `HALF_UP` to scale 2, then sum
rounded lines. Amount and aggregate overflow are checked after rounding; both use `NUMERIC(19,2)`.
For example two lines of `0.5 × 0.01` yield `0.01 + 0.01 = 0.02`. Existing exact-decimal JSON serialization
returns strings; requests support strings and the existing legacy JSON-number convention. Angular
submits decimal strings and no totals. Its labeled provisional preview uses integer arithmetic and
never substitutes for the backend response.

## HTTP contracts

All routes use existing session authentication, a sales-only OPERATOR authorization matcher, CSRF on writes and standard
API errors. Controllers expose application DTO values, never JPA entities. OpenAPI documents drafts,
optional association, line identity, exact decimals, rounding, pagination and errors. The required
`lines` array and its required item/quantity/price fields are explicit. `DraftOrderLineRequest` has a
separate schema from the response line, which also contains server-calculated amounts and live labels.

| Method | Route | Request/query | Success |
| --- | --- | --- | --- |
| POST | `/api/v1/sales` | `{customerId?, lines:[{id?, itemId, quantity, unitPrice}]}` | 201, Location, order |
| GET | `/api/v1/sales` | `q?`, `customerId?`, `from?`, `to?`, `page=0`, `size=20` | 200 page |
| GET | `/api/v1/sales/{orderId}` | UUID | 200 draft |
| PUT | `/api/v1/sales/{orderId}` | Complete draft replacement | 200 updated draft |

PUT follows current customer/catalog/supplier replacement conventions. Order response fields are
`id`, nullable `customerId`/`customerName`, `status`, `currency`, `lines`, `total`, `createdAt`, `updatedAt`.
Line fields are `id`, `itemId`, nullable live `itemName`/`unitOfMeasure`, `quantity`, `unitPrice`, `amount`.
Read-only inspection remains possible after references become inactive. Missing live labels do not
replace or lose the stable references. The page is `{content,page,size,totalElements,totalPages}`.

Search `q` is a trimmed, literal, case-insensitive partial match on the order UUID, bounded to 36
characters. Blank lists normally, LIKE metacharacters are escaped. Customer UUID filters directly.
Creation dates `from`/`to` are inclusive **UTC** calendar dates; reversed intervals are rejected. Lists
always select `DRAFT`, ordered by `createdAt DESC, id ASC`, using zero-based pages, default size 20,
and size 1–100. Page offsets exceeding the JPA integer-offset range are rejected. No state selector is introduced while only drafts are queryable.

400 errors use `VALIDATION_ERROR` with line/field details, `INVALID_ORDER_SEARCH_QUERY`,
`ORDER_CUSTOMER_NOT_FOUND`, `ORDER_CUSTOMER_INACTIVE`, `ORDER_ITEM_NOT_FOUND`,
`ORDER_ITEM_INELIGIBLE`, or existing invalid binding/body codes. Missing drafts use 404
`ORDER_NOT_FOUND`; editing a nondraft uses 409 `ORDER_NOT_EDITABLE`. Existing 401/403 contracts apply. The narrow new sales matcher precedes the generic authenticated API matcher; all existing routes retain their original authorization policy.

## Persistence, compatibility and recovery

V17 was the verified latest migration. V18 creates only `sales_order`, `sales_order_line` and two
justified order-page indexes. See [data model](../architecture/data-model.md). Order-to-line FK and
customer FK prevent dangling relational owners; item UUID is validated through the public catalog API.
Within sales, `(order_id,item_id)` is unique; this index also supports the line collection join.
SQL checks defend lifecycle values, positive quantity, nonnegative money, nonnegative position and
line amount consistency (`amount = round(quantity * unit_price,2)`). PostgreSQL's numeric columns
bound storage; application validation rejects excess input fraction digits before PostgreSQL can round.
Aggregate total consistency is calculated atomically by the sales write path.

The existing V17 application reads/writes its unchanged tables while V18 sales readers/writers use
new tables. No existing data or applied migration changes. Flyway runs once and retry is a no-op.
Application rollback retains new tables and draft data. No down migration or destructive contraction
is part of this task; committed-data recovery uses verified PostgreSQL backups. Tests use disposable
PostgreSQL/Testcontainers only, never the operational database.

## Angular and operator workflow

Lazy routes beneath the authenticated shell: `/sales`, `/sales/new`, `/sales/:orderId`, and
`/sales/:orderId/edit`. **Comercial → Pedidos** navigates to drafts. The feature HTTP service uses
existing Spring Boot APIs and authentication/CSRF interceptors; no database/provider SDK exists.

The list supports ID search, creation-date filters and pagination, cancels stale responses, corrects an
out-of-range nonempty page once, and shows loading, empty and retry states. The editor uses Material
and Reactive Forms. Separate bounded, pageable selectors search active customers and active finished
catalog products; only UNIT/mL results are offered. Ineligible results can produce an empty page;
operators can search more specifically or visit another page instead of losing access past a cap.
Customer selection is optional and clearable. A selected item has one editable quantity/price line;
adding a duplicate prompts editing the existing line. Removing/reordering does not duplicate identities.
The provisional total and stock disclaimer are explicit. Detail shows the server total and live labels.

All application copy and validation/error feedback is pt-BR. Invalid submission focuses the first
invalid input; adding/removing lines moves focus to the editable line or product search. Pending guards prevent duplicate writes; inputs/selectors/actions are disabled/read-only
while saving. Recoverable write failures preserve input and allow explicit resubmission. No optimistic
stock changes or stock-availability promises exist. See [operator guidance](../product/customer-orders.md).

## Tests and validation

Backend unit tests cover optional and active customers, missing/inactive/ineligible references, both
allowed units, unique items, empty/null inputs, stable IDs, foreign/reassigned IDs, timestamps, positive
quantity, integer/fraction limits, zero price, exact calculation, line HALF_UP rounding, post-rounding
line overflow, aggregate overflow and query bounds.

PostgreSQL/Testcontainers tests cover HTTP create/detail/list/update, retained values after live label
changes, line add/remove/reorder and identity persistence, public reference validation, escaped UUID
search, deterministic customer/date pagination, standard errors, session/authorization/CSRF, OpenAPI,
SQL checks/FKs/uniqueness and unchanged stock tables on successful/failed draft edits. Omitted customers
and legacy JSON-number inputs retain their contract; OpenAPI distinguishes required request fields from
server-calculated response values. V17→V18 upgrade
retains old data, validates retry and mixed old/new writers. Existing customer migration tests now
expect both additive V17/V18 migrations from V16; existing customer OpenAPI coverage retains its own
contracts without asserting that the new sales route must remain absent. Modulith verifies all modules.

Angular tests cover authenticated lazy routes, HTTP contracts/decimal strings, preview rounding and
limits, optional/clearable customer, product eligibility/search/paging, draft creation/edit prefill/stable
IDs, line management, input feedback/focus, duplicate-submit prevention, recoverable failure retaining
values, clearing feedback when switching drafts, list search/paging/stale responses/loading/empty/errors,
out-of-range page recovery, filter clearing, customer selector stale-response/error recovery, and detail
loading/not-found/retry.

Required validation: backend `./mvnw verify`; frontend `pnpm lint`, `pnpm test`, `pnpm build`;
repository documentation checks and `git diff --check`, full diff review and `git status --short`.

Validation on 2026-10-06: backend verify passed with 678 tests and no failures, errors or skips;
frontend lint passed, all 484 tests in 76 files passed, and the production build passed. Backup and
restore contract checks, required repository documentation and Markdown/YAML whitespace checks passed.
The build reports a nonblocking initial bundle warning (680.16 kB against the 500 kB warning budget,
below the 1 MB error limit). Backend tooling reports CycloneDX schema-keyword warnings, Lombok's
deprecated Unsafe use and Mockito dynamic-agent warnings; these do not fail verification.

## Deferred scope

Confirmation, cancellation, immutable confirmation snapshots, allocations, sale-to-movement references,
FEFO/stock integration, reservations, payment, pricing automation, taxes, fiscal documents, returns,
provider access, attachments, new dependencies and unrelated operational changes remain excluded.
