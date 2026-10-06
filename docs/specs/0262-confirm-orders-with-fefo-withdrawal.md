# Issue #262 — Confirm orders with FEFO withdrawal

## Sources and ownership

[Issue #262](https://github.com/PxS00/lavanda-flow/issues/262),
[issue #259](https://github.com/PxS00/lavanda-flow/issues/259),
[ADR 0012](../architecture/decisions/0012-define-v0.8-commercial-boundaries.md) and
[the #261 draft contracts](0261-register-customer-orders.md) define this slice.
`sales` owns the application transaction, persisted confirmation snapshots, allocations and lifecycle.
It depends only on public `customers`, `catalog`, `inventory` and `shared::error` contracts.
Inventory has no sales dependency and exclusively owns FEFO, eligibility, expiration, availability,
item/batch locks, balances and immutable movements. No dependency or module is added.

## Transaction and concurrency

`OrderManagement.confirm` opens one PostgreSQL transaction and pessimistically locks the order row
before validating the optional customer's current active state or invoking inventory. Only `DRAFT`
withdraws stock. `CONFIRMED` returns the stored result without inventory or live-label lookups;
`CANCELLED` fails with `ORDER_NOT_CONFIRMABLE`. The order UUID is the idempotency key.
Concurrent attempts serialize on the row. Draft replacement and cancellation use that same lock.

`SaleStockWithdrawal.withdraw` requires the caller transaction (`MANDATORY`). Its immutable input
lines contain item UUID, exact quantity and opaque `StockAuditReference(referenceType, referenceId,
referenceLineId)` values (`SALE`, order UUID, line UUID). Sales supplies stable UUID item order;
inventory validates input, acquires all item locks in that order, reads current finished-product
UNIT/mL eligibility and labels under those locks, then reuses `RegisterFefoWithdrawal` for each item.
Existing batch locking and FEFO ordering remain unchanged. Inventory returns product name/unit and
exact ordered `(batchId,movementId,quantity)` allocations. Every batch withdrawal produces an
immutable `CONSUMPTION` movement carrying the opaque reference.

All stock, movements, sales allocation rows, customer/product snapshots and `DRAFT -> CONFIRMED`
commit together. Validation, stock insufficiency, persistence and lock failures roll back the entire
transaction; no snapshot or partial stock effect remains. The order stays `DRAFT`. Retry is an explicit
client request using that same UUID; there is no automatic retry, reservation, saga or continuation.

At confirmation sales stores optional customer name/phone/email and product ID/name/unit, line ID,
exact quantity, entered unit price and existing server-rounded amount/total. Later contact/catalog
edits do not change confirmed results. Database triggers defend confirmed order and line snapshots,
allocations and referenced movements against update/delete, following production's history pattern.
Draft edits continue to use the original #261 decimal precision and HALF_UP amount contracts.

## HTTP and Angular

All routes retain existing OPERATOR/session/CSRF security and API error shape.

| Method | Route | Result |
| --- | --- | --- |
| POST | `/api/v1/sales/{orderId}/confirm` | 200 persisted confirmed order; no request body or separate token |
| GET | `/api/v1/sales/{orderId}` | Existing draft result or immutable confirmed result |
| POST | `/api/v1/sales/{orderId}/cancel` | 200 cancelled draft; no stock effects |

Confirmation returns the existing order fields plus nullable `customerPhone`, `customerEmail`,
`confirmedAt` and per-line `allocations[]` containing exact quantity, batch UUID and movement UUID.
Draft responses have null confirmation fields and empty allocations. Existing draft routes, filters,
precision and wire values remain compatible; the list remains DRAFT-only. OpenAPI documents the
additive routes, snapshots, idempotency, atomicity and errors. Missing orders return 404; cancelled
confirmation returns 409; `INSUFFICIENT_ELIGIBLE_STOCK` and `SALE_ITEM_INELIGIBLE` return 422;
customer validation and existing binding errors retain 400. A sales-only advice returns the existing
API error shape with 409 `ORDER_LOCK_CONFLICT` for pessimistic lock/deadlock/timeout failures and
500 `ORDER_PERSISTENCE_FAILED` for persistence/transaction failures, without exposing SQL details.
Confirmed editing/cancellation fails with 409 and no delete route exists.

The detail screen exposes explicit confirmation and renders only the backend result. Pending guards
and disabled controls prevent duplicate submission. Failures retain draft values and allow an explicit
same-order retry. Errors and operator copy are pt-BR; no FEFO or stock calculation runs in Angular.
A lost HTTP response can follow a committed transaction, so retry always queries/attempts the same
identity. Draft cancellation has an API contract but no new Angular cancellation workflow.

Physical returns or confirmed-data corrections are separate auditable operations. Confirmation and
cancellation never put stock back. Returns management, payment, fiscal features and unrelated work
remain excluded.

## Migration, compatibility and recovery

V19 expands the V18 schema only. Nullable bounded reference columns are added to `stock_movement`;
a check requires all three opaque reference values or none, and a partial unique index on
`(reference_type,reference_id,reference_line_id,batch_id)` rejects duplicate source-line/batch use.
Existing movements retain null references and unchanged HTTP representations.

Sales adds nullable customer snapshots/confirmation time and product label/unit snapshots.
`sale_allocation` stores sales-owned UUID identity, line FK, batch/movement UUID FKs, positive
`NUMERIC(19,6)` quantity and deterministic allocation position. Unique `(line_id,batch_id)` and
movement constraints defend duplicate allocation. SQL FKs protect actual inventory UUID existence;
no cross-module JPA relationship or inventory-to-sales FK is introduced. Inventory's opaque source
references have no polymorphic sales FK. Existing movements, production genealogy and draft values
are never backfilled, rewritten or deleted.

Deploy the additive migration before new application writers (normal startup Flyway ordering).
V18 inventory writers omit the new nullable columns; V18 draft writers remain valid. Old sales readers
are draft-only and cannot show confirmed sales, and old sales edits already reject nondrafts; route
confirmation traffic to the new version during rollout. Flyway retry is a no-op. Application rollback
retains additive schema and all confirmation history; an old application can operate drafts/inventory
but cannot display confirmed sale history. There is no destructive down migration or contraction.
Committed business-data recovery requires a verified PostgreSQL backup.

## Proof

PostgreSQL/Testcontainers tests cover exact multi-batch FEFO, ineligible dates, actual allocation and
movement audit references, snapshots after edits/deactivation, confirmed retries, concurrent row-lock
serialization, insufficiency on a later item, allocation persistence failure, batch-lock timeout after
an earlier withdrawal, explicit retry, cancellation, immutable database history, duplicate-consumption
constraints, security, HTTP persistence/lock error shape and OpenAPI. A V18-to-V19 migration test preserves movements, batches and
production genealogy and verifies legacy writers and migration retry. Unit tests verify confirmed
retries never call inventory or live lookups. Spring Modulith verifies dependency boundaries.

Angular tests cover the POST identity contract, successful backend result and confirmed allocations,
insufficient stock/server/network failures, explicit retry, pending duplicate prevention and resulting
state. Existing draft, inventory, customer and production regression suites remain enabled.

Required validation: `./mvnw verify`, `pnpm lint`, `pnpm test`, `pnpm build`, `git diff --check`, complete
diff review and `git status --short`. Validation uses disposable Testcontainers PostgreSQL only.

Validation on 2026-10-06: `./mvnw verify` passed with 694 tests, no failures/errors/skips;
`pnpm lint` passed, all 491 Angular tests in 76 files passed, and `pnpm build` passed.
`git diff --check` passed. The build retains the existing nonblocking initial bundle warning
(680.16 kB against a 500 kB warning budget, below the 1 MB error budget). Existing CycloneDX
schema-keyword, Lombok Unsafe, Mockito dynamic-agent and unchecked-query compiler warnings remain
nonblocking. No operational database, commit, push, publication or PR was used.
