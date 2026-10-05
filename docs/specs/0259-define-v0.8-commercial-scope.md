# Issue #259 — Define v0.8.0 commercial scope

## Objective and source of truth

Define the approved v0.8.0 customer, order, and sales contracts before production implementation. The product scope is in [`scope-v0.8.0.md`](../product/scope-v0.8.0.md); module and transaction ownership is in [ADR 0012](../architecture/decisions/0012-define-v0.8-commercial-boundaries.md). V1 remains as recorded in [`scope-v1.md`](../product/scope-v1.md).

## Domain and validation contract

### Customer

Persist only `id`, `name`, optional `phone`, optional `email`, active state, and audit timestamps. Do not require either contact method or require at least one. Normalize surrounding whitespace; reject blank names and bound name to 160 characters. Phone is trimmed, accepts a leading `+` and common display separators, removes spaces/parentheses/periods/hyphens, and stores an optional leading `+` followed by 7–15 digits. Email is trimmed, at most 254 characters, and must pass standard address syntax validation; email and phone are not unique identity keys. Search normalizes phone query separators the same way and compares name/email case-insensitively. No verification, government ID, address, notes, or profile data is stored.

Search is case-insensitive partial match across name, phone, and email with optional active-state filtering. Empty search returns the normal list. Follow existing paging: zero-based page, default size 20, maximum 100, deterministic name/ID ordering. The UI defaults to active customers but can expose inactive records for history/maintenance. Deactivation preserves the record and all sale references. Inactive customers cannot start a new draft or confirm an existing draft; historical sales remain readable using their snapshots.

### Order and sale

One sales-owned aggregate has stable order ID and states `DRAFT`, `CONFIRMED`, and `CANCELLED`. Drafts may be created/edited/cancelled without holding stock. Confirmation is the only normal stock-changing transition and turns the order into its confirmed sale history. Confirmed records are immutable and cannot be cancelled/deleted. Lines have stable UUIDs and reference one existing catalog item; allow at most one line per item and require a positive quantity. A draft must contain at least one line to confirm. This makes each line's FEFO allocation and audit references unambiguous.

At draft save and again at confirmation, validate through public customer/catalog lookups. A sellable item is active, `FINISHED_PRODUCT`, and has unit `MILLILITER` or `UNIT`. `UNIT` means a separately stocked package/presentation; `MILLILITER` means bulk finished product. Quantity follows existing inventory precision (`NUMERIC(19,6)`, at most 13 integer and 6 fractional digits). No conversion, rounding-to-presentation, or unit substitution is performed. Unavailable/inactive item, ineligible category/unit, or inactive customer blocks confirmation.

### Price and total

The operator supplies a non-negative BRL unit price for every draft line; no catalog price, discount, tax, payment, or price automation exists. Accept at most four fractional digits and persist unit price as `NUMERIC(19,4)`. Compute `quantity × unitPrice` with exact decimal arithmetic, round each line amount to scale 2 using `RoundingMode.HALF_UP`, and sum the rounded line amounts for the sale total. Persist line amount and sale total as `NUMERIC(19,2)` with overflow rejected. Backend response is authoritative; any Angular calculation is a provisional preview only.

On successful confirmation snapshot customer ID/name/phone/email and, per line, item ID/name, category, unit/presentation, quantity, unit price, and rounded line amount. Currency is fixed to BRL for v0.8.0. Product display labels and contacts are historical values captured at confirmation. Later customer/catalog edits never change confirmed sale history.

## State, stock, and transaction contract

| Event                                        | Allowed state | Result                                      | Stock effect                                                     |
| -------------------------------------------- | ------------- | ------------------------------------------- | ---------------------------------------------------------------- |
| Create/edit order and lines                  | `DRAFT`       | Remains `DRAFT`                             | None; no reservation                                             |
| Cancel draft                                 | `DRAFT`       | `CANCELLED`                                 | None                                                             |
| Confirm                                      | `DRAFT`       | `CONFIRMED` sale                            | All eligible line quantities withdrawn atomically                |
| Repeat confirm                               | `CONFIRMED`   | Returns stored confirmation                 | None; never calls inventory again                                |
| Cancel confirmed sale                        | `CONFIRMED`   | Rejected as conflict                        | None                                                             |
| Physical return or confirmed-data correction | `CONFIRMED`   | Sale unchanged; separate audited correction | Only a separately accepted inventory correction can change stock |

The sales application service begins one PostgreSQL transaction, locks the order, validates its current customer/product state and all lines, computes authoritative snapshots/totals, then calls one public inventory sale-withdrawal API synchronously. The confirmation timestamp comes from the application `Clock`, never the client. It requests FEFO by product ID/quantity, with product IDs processed in stable UUID order. Inventory applies its established item/batch locking policy, current eligibility and expiry check using the same application `Clock` (`expiresAt <= today` is expired), and all-or-nothing allocation. Returned batch allocations and movement IDs are persisted on sale lines before commit.

Sales confirmation, stock balances, immutable movements, line allocations, snapshots, and `CONFIRMED` status commit together. Any failure, including insufficient eligible stock for one line, rolls the entire transaction back; the order remains `DRAFT` and no earlier line is withdrawn. On insufficient stock, return the existing standard API error shape with a conflict/business code and available/required facts safe for operators; do not leak persistence details.

The persisted order ID is the retry identity. A request that reaches a committed confirmation but loses its response can repeat the same confirm request and receives the stored sale/allocation result. A concurrent confirmation waits on the order lock, then observes `CONFIRMED` and returns that result. A transaction that failed rolls back and permits a later explicit retry of the same draft. No automatic retry loop is added for lock timeout/deadlock; those failures roll back and use the existing infrastructure error handling.

The inventory API receives exact item quantities plus opaque `SALE` reference values `{saleId, saleLineId}`. It returns `{batchId, quantity, movementId}` allocations. Each immutable movement stores the reference, and the sales allocation row stores the matching movement ID. The sales module never writes inventory tables or selects batches itself. A unique constraint on `(referenceType, referenceId, referenceLineId, batchId, movementType)` for referenced sale consumption is defense in depth; order locking and persisted state prevent ordinary duplicate confirmation.

### Cancellation, returns, and corrections

Cancelling a draft only marks it cancelled. Confirmed sale data and movement history are never deleted or overwritten. A physical return requires a separate operator action after goods are physically received and inspected; it is not implied by cancelling a sale, and payment refunds are out of scope. If accepted back into stock, inventory records a new positive adjustment against the original allocated batch, verifies current eligibility/expiration, and records the same sale-line audit reference. Rejected/non-resalable goods do not increase stock. Confirmed commercial values are immutable; v0.8.0 has no sale edit, refund, or full returns-management workflow. Corrections to physical stock use a new inventory movement and retain the old sale and movement history.

## Ownership and allowed dependencies

See [ADR 0012](../architecture/decisions/0012-define-v0.8-commercial-boundaries.md). `customers` owns contacts and publishes immutable lookup values. `sales` owns order/sale lifecycle, line data, snapshots, total calculation, and allocation history; it depends only on public `customers`, `catalog`, `inventory`, and narrow `shared` APIs. `catalog` and `inventory` do not depend on `sales`; inventory receives only opaque reference values and owns all batch, movement, FEFO, expiration, and concurrency rules. No cross-module entity/repository/JPA imports or direct frontend database access are allowed.

## Persistence and HTTP/UI plan

Use bounded relational fields and forward-only Flyway migrations after current V16. Add customer storage and a `(active, name, id)` index for active listing and deterministic pagination; the expected small-business volume uses case-insensitive substring contact search without a specialized text-index extension. Add sales aggregate, line, and allocation storage with UUID references, state checks, positive-quantity/nonnegative-price checks, and indexes for customer/state/confirmation-date history and line/batch allocation lookups. Add nullable opaque reference fields to `stock_movement` and the justified unique index for sale-linked consumption. Preserve every applied migration; no existing rows or V1 data are rewritten. Additive schema permits application rollback while retaining tables/columns. Do not add down migrations or destructive contraction as part of these slices. Keep PostgreSQL backups as recovery authority for any future irreversible data operation.

Expected authenticated contracts (exact DTO names and final route shapes are set in implementation issues):

- `GET/POST /api/v1/customers`, `GET/PATCH /api/v1/customers/{id}`, and explicit activate/deactivate actions; list supports `q`, `active`, `page`, and `size`.
- `GET/POST /api/v1/sales`, `GET/PATCH /api/v1/sales/{id}` for draft work, and `POST /api/v1/sales/{id}/confirm` or `/cancel`; list/history supports customer, state, date, page, and size filters.
- Existing `/api/v1` errors, same-origin session authentication, authorization, CSRF token flow, and HTTP contracts remain unchanged. New writes require an authenticated operator and CSRF protection.

Angular uses feature data-access services and Reactive Forms. Customer pages support search, paging, create/edit, activation state, loading/error/empty states. The order editor selects active customers and finished-product catalog items, captures unit-compatible quantities and explicit BRL unit prices, and labels preview totals as provisional. Confirmation disables duplicate submission while pending, shows server-confirmed allocations/totals, and lets the operator retry the same order after recoverable failure. Insufficient stock and inactive references remain visible in pt-BR; do not optimistically display stock changes.

Do not create a customer document store, attachment service, storage bucket, provider SDK, or fiscal subsystem. Fiscal feasibility is an optional post-delivery review only; no SEFAZ call, certificate, XML/PDF, or legal-compliance claim is part of implementation.

## Ordered implementation slices

Each follow-up issue should deliver its Flyway change, backend contract, authenticated Angular workflow, documentation, and meaningful tests together. Keep each slice small enough to review and independently usable.

1. **Customer contacts:** add the `customers` module, additive customer schema, public lookup, validated CRUD/search/deactivation API, Angular maintenance/search flow, OpenAPI and operator guidance. Test validation/search/paging/history-preserving deactivation, authenticated/CSRF behavior, PostgreSQL constraints, and Modulith boundaries.
2. **Draft orders and pricing:** add the `sales` module and draft/line persistence, public customer/catalog lookups, line identity/unit/price validation, exact backend totals and draft APIs; add Angular order editor/history. Test catalog/customer eligibility, decimal limits and rounding, immutable IDs, and draft edits/cancellation producing no inventory effects.
3. **Atomic confirmation:** add the public inventory FEFO command and additive movement audit references, sale allocation persistence, transaction-joining confirmation API, retry semantics, and Angular confirmation/result/error flow. Test with PostgreSQL/Testcontainers that full success records allocations/movements, insufficient stock and injected failures roll back all lines/state, concurrent same-order confirmation withdraws once, competing stock use never goes negative, FEFO skips expired batches, and retry returns persisted results. Frontend tests cover pending/duplicate submit, retry, insufficient-stock and conflict errors.
4. **Sale history and physical corrections:** complete paginated detail/history, immutable historical snapshots, draft cancellation, and the inspected-return/correction contract through public inventory APIs; add corresponding Angular history/correction flow only for these approved cases. Test deactivated/renamed references, cancelled drafts without stock effects, confirmed sale immutability, return eligibility/expiration, movement references, and frontend error handling.

Do not start a slice before its public contracts and preceding persistence are merged into the integration branch. The dependency order is customer lookup → draft order → atomic confirmation → history/corrections; fiscal feasibility follows only if separately requested and cannot block v0.8.0.

## Acceptance checklist for implementation issues

- [ ] Customer field limits, normalization, validation, search, paging, deactivation, and history retention match this specification.
- [ ] Only eligible active `FINISHED_PRODUCT` identities with `MILLILITER` or `UNIT` are sold; no unit conversion exists.
- [ ] Server calculations use exact decimals, line-level `HALF_UP` rounding to two decimals, and backend-authoritative totals.
- [ ] Confirmed lines/customer preserve historical values; every line's concrete batch allocations and movement IDs are queryable.
- [ ] Draft, confirmation, cancellation, retry, concurrency, insufficient stock, rollback, and return behavior match the state table.
- [ ] Inventory remains the only owner of FEFO, expiration, stock balances, movements, and stock locks.
- [ ] Flyway changes are additive and PostgreSQL-backed; module boundaries remain acyclic and public-only.
- [ ] Auth/session/CSRF/error contracts remain intact; all planned UI copy is pt-BR.
- [ ] Meaningful PostgreSQL/Testcontainers transaction/concurrency tests and Angular error/retry tests accompany the relevant slices.
- [ ] No fiscal baseline, payment, reservation, CRM, attachment, blob, provider, or unrelated ERP capability is added.
