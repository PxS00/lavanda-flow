# Issue #276 — Register sale-linked stock corrections

## Authority and implementation map

[Issue #276](https://github.com/PxS00/lavanda-flow/issues/276), [#259](0259-define-v0.8-commercial-scope.md),
[#262](0262-confirm-orders-with-fefo-withdrawal.md), [#275](0275-expose-paginated-sale-history.md),
[commercial scope](../product/scope-v0.8.0.md) and ADR 0012 govern this slice.
Sales owns sale/allocation identity, cumulative authorization and the caller transaction. Inventory
owns current eligibility, item/batch locks, balances and movements. Existing `ADJUSTMENT_IN`,
`Batch.addQuantity`, `StockAuditReference`, order locks and immutable history are reused.
The generic adjustment use case cannot enforce accepted-return eligibility and is left unchanged.
No new module, movement enum, payment/refund, cancellation effect or sale editing is introduced.

## HTTP and identity

`PUT /api/v1/sales/{orderId}/stock-corrections/{correctionId}` accepts
`{lineId, batchId, quantity, reason, inspected, accepted}`. UUID correction identity is generated
once by the client; identity is scoped to the sale (`orderId`, `correctionId`). Quantity is an
exact positive decimal fitting NUMERIC(19,6), in the original line unit, without rounding or
conversion. Reason is trimmed, required and at most 255 characters. Both booleans must explicitly
be true: this endpoint records physically received, inspected goods accepted as saleable, not a
rejection lifecycle. Missing/false decisions fail before any stock effect. Actor is the authenticated
principal (not client input). Response 200 contains persisted `id, saleId, lineId, batchId, quantity,
reason, inspected, accepted, actor, occurredAt, movementId`. No mutable resulting balance is stored.

`GET /api/v1/sales/{orderId}/stock-corrections` returns this sale's persisted corrections ordered
by occurredAt then ID, including all audit identities and decisions. Direct sale detail remains
unchanged. Both routes require OPERATOR/session authorization; PUT requires existing CSRF.
OpenAPI describes both routes and the existing public error shape.

Only CONFIRMED sales qualify. Missing sale: 404 ORDER_NOT_FOUND. Invalid body: 400
INVALID_SALE_CORRECTION. Unknown/mismatched sale-line/batch: 400 SALE_CORRECTION_ALLOCATION_INVALID.
Nonconfirmed: 409 SALE_CORRECTION_NOT_ALLOWED. Quantity above allocation less all accepted
corrections: 409 SALE_CORRECTION_LIMIT_EXCEEDED. Same identity with changed normalized request:
409 SALE_CORRECTION_ID_CONFLICT. Identical retry returns stored result, including original actor
and timestamp, without rechecking current eligibility or crediting again, even after expiry.
New ineligible return: 422 SALE_RETURN_INELIGIBLE. Existing ORDER_LOCK_CONFLICT (409) and
ORDER_PERSISTENCE_FAILED (500) sanitize infrastructure failures; explicit same-identity retry only.

## Eligibility, transactions and concurrency

Sales locks the order row first, then reads corrections and validates the original immutable
line/allocation. It sums accepted quantities for that exact line/batch under the order lock.
Different correction IDs may make multiple partial returns up to the original allocation exactly.
All attempts on one sale serialize, including identical retries, preventing cumulative over-return.
No confirmed order/line/allocation row is updated by a correction.

The new public `SaleStockReturn` inventory API joins the caller transaction (MANDATORY).
Input carries item UUID, original batch UUID, exact quantity, reason and opaque SALE/line reference.
Inventory locks the catalog item, then the selected batch, in the existing item-before-batch order.
It reads current catalog details and the locked batch: active FINISHED_PRODUCT, UNIT or MILLILITER,
matching item identity, dated batch with expiresAt strictly after LocalDate.now(application Clock).
Undated and expired-on-today batches are rejected. Zero current balance is allowed: replenishing
a fully consumed original batch is the purpose of this operation; FEFO's positive-balance predicate
is a withdrawal availability rule, not a return prerequisite. No manual override exists.
Inventory adds quantity and writes a new ADJUSTMENT_IN with original sale/line/batch references.
Sales stores the returned movement ID and timestamp in its separate immutable correction record.
Correction insert, balance and movement commit atomically; every exception rolls back all effects.
Existing batch-only writers cannot invert this lock order because they acquire no item lock later.

## Persistence, migration and recovery

V1–V19 remain byte-for-byte unchanged. V20 creates `sale_stock_correction`, keyed by
`(sale_id,id)`, with composite FK `(line_id,batch_id)` to the original allocation, sale/line
association FK, unique movement FK, positive exact quantity, nonblank bounded reason/actor,
true-only inspected/accepted checks and immutable update/delete trigger. The correction table
stores authorization/audit metadata, not a second inventory ledger or commercial snapshot.

V19's unique index covers every referenced movement and therefore blocks even the first return.
V20 first creates a replacement unique index on the same source/line/batch tuple for all referenced
movements except ADJUSTMENT_IN, then drops the old index, within Flyway's PostgreSQL transaction.
CONSUMPTION retains exactly its original uniqueness; distinct positive correction movements may
coexist. Merely adding movement type would still prohibit a second partial return and is rejected.
Correction identity PK plus sale locking and unique movement FK defend correction deduplication.
No existing row is rewritten, and no movement immutability trigger is removed.

Deploy V20/backend before the new Angular client. Old confirmation/inventory writers and readers
continue using unchanged columns/contracts; the replacement index still rejects duplicate
consumption. Mixed-version operation is safe, but correction traffic requires the new backend.
Migration failure rolls back DDL; retries validate/no-op after success. Application rollback keeps
V20 and accepted audit records; old UI loses correction access but cannot undo stock. Do not
recreate V19's old index after returns exist. No down migration/contraction; committed-data recovery
uses verified PostgreSQL backup procedures.

## Angular and proof plan

Confirmed-sale detail embeds one small correction form with original line/batch selection,
exact-decimal quantity, reason and separate explicit inspection/acceptance checkboxes. It performs
input validation only; eligibility and cumulative limits are backend authority. Pending disables
inputs/submission. A failed request retains its immutable payload and correction ID for explicit
retry (including lost responses); edits cannot silently issue a new identity. Success displays
persisted audit details; a new action permits another partial return. History has loading/error/
retry/empty states in pt-BR and survives page reload. No optimistic stock or refund display.

PostgreSQL/Testcontainers proof covers accepted full/partial/exact-limit returns, over-return,
expired today/past/undated/inactive/ineligible batches, missing and mismatched references, rejected
decisions, invalid decimals, identical/changed retries, concurrent retries and distinct corrections,
persistence rollback after inventory effects, movement correlation, confirmed immutability,
security/CSRF, OpenAPI and V19→V20 compatibility/duplicate-consumption protection. Angular covers
selection, validation, explicit decisions, pending duplicates, audit history, success and explicit
same-identity retry. Run backend verify, frontend lint/test/build, diff check, full diff and status.

These are narrow implementation decisions within ADR 0012; no unresolved structural decision
requires a broader architecture or an approval pause.

## Validation result (2026-10-19)

`./mvnw verify` passed: 722 tests, zero failures/errors/skips, using disposable PostgreSQL
Testcontainers. New coverage adds 23 correction integration cases and one V19→V20 migration case.
The correction integration context supplies a fixed application Clock, isolating its committed
fixtures from existing rollback-based tests. Legacy migration tests upgrade their original V16/V17/V18
baselines through the complete current schema; the dedicated V19→V20 migration test independently
proves the index and constraint transition.
Spring Modulith and existing confirmation, inventory, security and immutable-history suites pass.

`pnpm lint`, all 511 Angular tests across 77 files, and `pnpm build` passed. Seven new form cases
and one client contract case cover the correction workflow; existing confirmed-detail expectations
now include the independent correction form. `git diff --check` passed and the full tracked diff and
new files were reviewed. Final status contains only this issue's source, tests, migration and docs.

The initial sandboxed backend attempt could not initialize Docker; verification succeeded with
Docker access. An initial full run exposed shared committed-test data; the fixed-Clock correction
context resolves that isolation problem without changing existing production or rollback-test behavior.
The existing nonblocking bundle warning remains (680.36 kB initial bundle versus 500 kB warning
budget, below the 1 MB error budget). Existing backend tooling warnings remain nonblocking.
No browser/manual accessibility audit or production-volume migration benchmark was run.
No production/Supabase resources, commit, push or PR were used. No acceptance criterion or
architectural decision remains unresolved; rollout must retain V20 when rolling back the application.

## Review follow-up — LAN HTTP identities and complete upgrade coverage

The approved operator origin is plain HTTP on the trusted LAN, so `crypto.randomUUID` may be
unavailable. The component uses it when exposed and otherwise creates an RFC 4122 v4 UUID from
16 `crypto.getRandomValues` bytes, setting version 4 and RFC variant bits before formatting. There
is no dependency, timestamp or non-cryptographic random source. Generation occurs only when
creating the logical attempt; pending submissions and failed HTTP response retries retain that same
identity and payload. A successful correction permits a fresh identity for a new operation.

If cryptographic generation is unavailable or throws, the form remains editable and shows a pt-BR
error stating that no return was sent. It creates no attempt and sends no stock-changing request.
An explicit retry may generate an identity after the browser capability becomes available.
Regression tests exercise native generation, fallback formatting/masks and successful submission,
lost-response retry identity, new-operation identity, pending guards and missing/throwing crypto APIs.

The final upgrade paths in CustomerMigrationTest, OrderMigrationTest and
SaleConfirmationMigrationTest no longer stop at V19. Their original V16, V17 and V18 baselines
require respectively four, three and two migrations to reach V20. Existing legacy-data, writer,
movement, production-genealogy, compatibility and migration-retry assertions remain intact. The
separate V19→V20 test is unchanged, as are V1–V19, V20 and inventory transaction/audit invariants.

Follow-up validation on 2026-10-19: `./mvnw verify` passed all 722 tests with zero failures,
errors or skips, including all three legacy upgrades through V20 and the dedicated V19→V20 test.
`pnpm lint`, all 518 Angular tests in 77 files, and `pnpm build` passed. Seven added UUID cases
exercise the native path, fallback masks/format/success, retry/new identity/pending behavior and
four unavailable/throwing cryptographic API cases. `git diff --check` passed; tracked and untracked
issue files were reviewed. Only the form, its regression tests, localized generation error, three
migration tests and this spec changed during the follow-up. The initial sandboxed backend run
could not attach the test agent or access Docker; the complete run passed outside that sandbox.
The nonblocking initial bundle warning remains (680.35 kB versus the 500 kB warning budget).
No physical notebook/tablet browser check or production access was performed; no commit, push or PR.
