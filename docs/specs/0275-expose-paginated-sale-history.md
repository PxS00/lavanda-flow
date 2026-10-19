# Issue #275 — Expose paginated sale history

## Authority and scope

[Issue #275](https://github.com/PxS00/lavanda-flow/issues/275) delivers history from
[spec #259](0259-define-v0.8-commercial-scope.md), after the intentionally draft-only
[#261](0261-register-customer-orders.md) and [#262](0262-confirm-orders-with-fefo-withdrawal.md)
slices. ADR 0012 assigns queries and immutable history to `sales`. No separate aggregate,
stock writes, confirmation/cancellation rule changes, returns (#276), dependencies or provider access.

## Additive query contract

`GET /api/v1/sales` retains its response and adds optional `status` (`DRAFT`, `CONFIRMED`,
`CANCELLED`), `confirmedFrom` and `confirmedTo` (ISO calendar dates). Omitted status means
DRAFT, including when other legacy filters are supplied. Unknown enum values are invalid; blank status follows the existing optional-binding convention (omitted);
no all-status mode or implicit status inference is introduced.

`q` remains a trimmed, case-insensitive literal partial UUID (maximum 36 characters), with
LIKE metacharacters escaped. `customerId` remains an optional exact UUID, including inactive
customers. `from`/`to` always filter creation, never confirmation. `confirmedFrom`/`confirmedTo`
filter persisted confirmation time and require explicit `status=CONFIRMED`. Both independent
ranges combine by intersection with all other predicates. Each bound is optional. Dates are
inclusive UTC days: lower midnight included, midnight after the upper date excluded. Reversed
ranges and maximum upper dates are rejected with `INVALID_ORDER_SEARCH_QUERY` and field details;
malformed dates/UUIDs/status use existing binding errors (`INVALID_REQUEST_PARAMETER`). No
client timestamp or application-time-dependent default is introduced.

Every status uses existing `createdAt DESC, id ASC` ordering, zero-based page, size 20 by default,
size 1–100, and the existing integer-offset bound. Confirmation filters do not change sorting.
Page shape and exact-decimal string serialization remain unchanged. Existing clients omitting new
filters keep identical draft visibility, dates, ordering and paging. OpenAPI describes every addition.

`GET /api/v1/sales/{orderId}` reuses persisted confirmed snapshots, totals, allocation quantities,
batch IDs and movement IDs without live recomputation. Cancelled records become readable at the
same route, with cancelled state and no edit/confirm controls; they retain draft live labels,
not confirmation snapshots. Missing IDs still return 404. Confirmed mutation restrictions and
all session/OPERATOR/CSRF/error contracts remain unchanged.

## Implementation and compatibility map

1. Extend the sales query value, controller binding/OpenAPI and JPA predicates. Keep the legacy
   Java constructor for existing callers. Existing confirmation and cancellation writers are unchanged.
2. Reuse sales result mapping and filter live bulk lookups to unconfirmed records; confirmed
   readers use persisted history only. Reuse bounded line loading for every status.
3. Extend Angular DTO/client and existing list with labelled status selection, customer lookup
   including inactive contacts, independent UTC creation/confirmation dates, reset and paging.
   Reuse detail navigation and snapshot/allocation rendering; label cancelled records explicitly.
   Direct edit URLs for CONFIRMED/CANCELLED redirect to the read-only detail before form
   population; submission independently requires a new order or a loaded DRAFT.
4. Add PostgreSQL and Angular regression coverage, update operator and commercial status guidance,
   then run the required backend/frontend validation and inspect the complete diff/status.

Persistence shape and writers do not change. V18 already indexes `(status, created_at DESC, id)`
and `(customer_id, status, created_at DESC, id)`; both serve the selected status/customer page
ordering. V19 adds confirmation timestamps but no confirmation index. Confirmation dates are an
additional predicate on those bounded status queries; no measured performance requirement justifies
another index in this slice. No Flyway migration, backfill, down migration or destructive contraction.
Forward rollout deploys the additive backend before the new UI. Old UI/API clients retain the
legacy default; new filters require the new backend. Rollback restores the previous UI/backend
and retains every record; old clients lose history discovery but data and stock remain intact.

## Test matrix

| Area | Proof |
| --- | --- |
| Compatibility | Omitted filters stay draft-only; existing UUID escaping, customer, creation bounds, 20/100 sizes and offset validation |
| Status/history | Explicit confirmed and cancelled queries; customer isolation; detail navigation without prior UUID |
| Date intersection | Inclusive UTC midnight and last microsecond; next-day exclusion; independent open bounds; creation vs confirmation intersection |
| Pagination | Multiple confirmed records sharing creation time; UUID tie-break, repeated pages, counts and out-of-range page |
| Validation/security | Invalid enums/dates/combinations/ranges, standard errors, anonymous 401 and nonoperator 403, existing write CSRF suite |
| Historical reads | Confirm then rename/deactivate customer/product; list and detail preserve exact snapshots, totals, allocation and movement identities; reads change no balances/history |
| Angular | New query serialization, status/customer/date filters, inactive customer lookup, reset, paging, stale responses, links, loading/empty/error/retry, cancelled read-only detail |
| Editor protection | Direct edit access redirects CONFIRMED/CANCELLED without an editable form; submission rejects both states independently of navigation; DRAFT editing remains enabled |
| Query count | Six confirmed sales with two persisted allocations each; cold-context pages of 1 and 4 sales use 3 statements each, partial final page uses 2; exact history, counts and distinct IDs preserved |
| Regressions | Existing draft edit, explicit confirmation retry, FEFO, transaction/concurrency, immutable history and Modulith suites |

Required checks: `./mvnw verify`; `pnpm lint`; `pnpm test`; `pnpm build`;
`git diff --check`; complete `git diff`; `git status --short`. No commit, push or PR.

## Validation result (2026-10-19)

Backend `./mvnw verify` passed against disposable Testcontainers PostgreSQL, including the
sales history, confirmation/concurrency, migration, security and Modulith suites (698 tests,
zero failures, errors or skips). Frontend
`pnpm lint`, all 503 tests in 76 files, and `pnpm build` passed. `git diff --check` passed and
the complete diff was reviewed. The initial backend attempts could not initialize Docker;
validation succeeded after local Docker Desktop/WSL became available. No production database
or Supabase resource was accessed.

The existing initial bundle warning remains: 680.16 kB against the 500 kB warning budget
(below the 1 MB error threshold). Existing backend tooling warnings remain nonblocking.
No browser/manual accessibility audit or production-volume query benchmark was run.
No commit, push, PR, Git write, schema migration or #276 implementation was performed.

Review follow-up: four Angular regression cases reproduced the non-DRAFT editor/submission
defect before the fix and pass afterward. Existing DRAFT edit coverage also asserts no redirect.
The PostgreSQL regression creates six actual confirmations, each with two allocations, and clears
the persistence context/statistics before each history read. Both full pages (sizes 1 and 4)
execute exactly 3 statements; the partial final page executes 2 because Spring Data infers its
count. Exact stored results, allocations, ordering, distinct IDs and total count 6 are verified.
No backend production change was needed for these review findings.
