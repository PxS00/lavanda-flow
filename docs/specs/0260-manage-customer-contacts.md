# Issue #260 — Manage customer contact records

## Status and source of truth

Implemented customer vertical slice on `feature/260/manage-customer-contacts`, based on `develop`
commit `64c8e2c66e0a4c801b6262d6113591f9bdb1fa0e` (issue #259).
[Issue #260](https://github.com/PxS00/lavanda-flow/issues/260) is authoritative, refined by
[spec #259](0259-define-v0.8-commercial-scope.md) and
[ADR 0012](../architecture/decisions/0012-define-v0.8-commercial-boundaries.md).

## Objective and delivered ownership

Authenticated operators can create, detail, edit, search, page, activate and deactivate minimal customer
contacts. `customers` owns contact normalization, validation, persistence and transactional maintenance.
Only `shared::error` is an allowed dependency. `CustomerLookup.findById(UUID)` returns an immutable
`CustomerSnapshot(id, name, phone, email, active)` for active or inactive customers, or empty for missing
IDs. It joins an existing caller transaction without exposing JPA or internal repositories.

`CustomerContact` trims surrounding whitespace, turns absent/blank optional values into null and removes
only the approved phone display separators. Application validation uses Jakarta `@NotBlank`, `@Size`,
`@Pattern` and standard `@Email` on these normalized values before writes. Name is limited to 160
characters; normalized phone is optional `+` plus 7–15 digits; email is at most 254 characters. Neither
contact method is required or unique. Invalid phone characters are rejected rather than discarded.

## HTTP contracts

All endpoints use existing session authentication. Writes require the existing CSRF cookie/header flow.
Existing authorization and error handling are unchanged. OpenAPI describes the new DTOs, routes, paging,
normalization and error responses when enabled by the existing local profile.

| Method | Route | Request/query | Success |
| --- | --- | --- | --- |
| POST | `/api/v1/customers` | `{name, phone?, email?}` | 201 + Location + customer |
| GET | `/api/v1/customers` | `q?`, `active?`, `page=0`, `size=20` | 200 page |
| GET | `/api/v1/customers/{customerId}` | UUID | 200 customer, active or inactive |
| PUT | `/api/v1/customers/{customerId}` | Complete `{name, phone?, email?}` | 200 customer |
| POST | `/api/v1/customers/{customerId}/activate` | No body | 200 active customer |
| POST | `/api/v1/customers/{customerId}/deactivate` | No body | 200 inactive customer |

PUT follows existing catalog/supplier replacement conventions rather than introducing partial PATCH
semantics. It preserves UUID, creation time and active state; omitted/blank optional fields are cleared.
Explicit state actions are idempotent and preserve timestamps on repeated actions. No DELETE exists.

A customer response contains `id`, `name`, nullable `phone`/`email`, `active`, `createdAt` and `updatedAt`.
The page shape is `{content, page, size, totalElements, totalPages}`. Page numbers are zero-based and
nonnegative, size must be 1–100 (default 20). Search trims `q`; blank lists normally. Name/email use
case-insensitive substring matches. Phone searches remove the same separators as input without imposing
full-phone digit bounds on a partial query. LIKE metacharacters are escaped, and separator-only phone
patterns cannot match every row. Optional `active` selects either state; absent includes both. Ordering
is database name order then UUID, consistent with the `(active, name, id)` index.

Standard errors include 400 `VALIDATION_ERROR` with contact field details,
`INVALID_CUSTOMER_SEARCH_QUERY` with paging details, `INVALID_REQUEST_PARAMETER` for invalid bindings,
404 `CUSTOMER_NOT_FOUND`, 401 `AUTHENTICATION_REQUIRED`, and 403 `CSRF_VALIDATION_FAILED`.

## Persistence, compatibility and recovery

V16 was the actual latest migration. V17 creates only `customer` with bounded relational contact fields,
NOT NULL identity/name/state/audit fields, CHECK constraints and `idx_customer_active_name_id`.
See [data model](../architecture/data-model.md). Audit instants come from the application `Clock`.
Edits and state actions lock the same row, preserving identity and creation time and preventing one
operation from overwriting another operation's fields.

Migration is additive: old V16 application readers/writers continue to use their existing tables;
new readers/writers use customers after V17. Flyway applies it once; a second migration run is a no-op.
An application rollback leaves the new table/data in place. No applied migration, operational data,
existing contract or release metadata is changed. Irreversible data recovery remains governed by verified
PostgreSQL backups; no down migration or contraction is part of this issue.

## Angular workflow

Customer routes are lazy children of the existing authenticated application: `/customers`,
`/customers/new`, `/customers/:customerId` and `/customers/:customerId/edit`. The navigation adds
**Clientes** to **Cadastros**. The typed feature data-access service calls Spring Boot through HttpClient;
Reactive Forms, Material and Signals/RxJS follow existing conventions.

The list defaults to active contacts, supports search/all/inactive filters and deterministic paging,
cancels stale filter requests and corrects an out-of-range page once. A shared create/edit form uses
immediate validation, accessible labels and focus on the first invalid control. Pending submissions
are blocked; recoverable failures preserve form input. Detail activation/deactivation changes displayed
state only after server success and retains old state on failure. All operator copy and errors are pt-BR.
See the [operator workflow](../product/customer-contacts.md).

## Test coverage and validation

- Unit application tests: name-only/independent optional fields, trimming, blank and bounded names,
  phone syntax/normalization/digit boundaries, standard email syntax/254–255 bounds, audit Clock,
  identity preservation, idempotent state changes, missing IDs and paging validation.
- PostgreSQL/Testcontainers integration: real HTTP create/detail/update/state transitions, retained row,
  duplicate contact values, immutable public lookup for both states/missing IDs, contact search, literal
  wildcards, active filters, deterministic pages/defaults/limits, SQL constraints, authentication/CSRF and
  generated OpenAPI. A separate migration test upgrades V16, retains old data, retries Flyway and exercises
  old/new writers. Existing Spring Modulith verification includes customers.
- Angular tests: HTTP contracts, active defaults, list/search/filter/paging/loading/empty/error/retry and
  stale responses; create/edit/prefill/validation/focus/name-only contacts, successful save, pending guards,
  recovery preserving input; detail/status action success/failure/retry; navigation/authenticated routes.

Required final commands: `./mvnw verify` in backend; `pnpm lint`, `pnpm test`, `pnpm build` in frontend;
repository documentation checks and `git diff --check`, `git diff`, `git status --short`.

## Scope and unresolved decisions

No structural decision remains unresolved. Sales, orders, snapshots, sale-to-customer foreign keys,
stock effects, payments, CRM, fiscal capabilities, attachments and provider SDKs remain planned or
excluded under #259. No sales table is created merely to simulate future history. No dependencies,
commits, pushes, PRs or release metadata changes are included.
