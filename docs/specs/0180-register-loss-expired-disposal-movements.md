# Issue #180 — Register loss and expired-disposal movements

## Objective

Complete the V1 backend stock-maintenance workflow with explicit, auditable operations for physical loss and expired-stock disposal.

The implementation must reuse the existing inventory batch balance, immutable movement history, pessimistic locking, exact-decimal transport, transaction, and error-contract architecture. It must not turn `MovementType` into caller-controlled input and must not change existing entry, withdrawal, adjustment, FEFO, production-stock, or movement-history behavior.

## Source of truth

Apply, in order:

1. GitHub issue #180;
2. `AGENTS.md`;
3. `backend/AGENTS.md`;
4. `docs/product/scope-v1.md`;
5. `docs/domain/domain-model.md`;
6. `docs/architecture/data-model.md`;
7. `docs/architecture/inventory-package-structure.md`;
8. `docs/inventory/movement-history.md`;
9. `docs/specs/0171-preserve-exact-decimal-quantities.md`;
10. current inventory movement application/domain/web implementation;
11. this specification for the approved implementation details of #180.

Issue #180 remains authoritative for objective, scope, acceptance criteria, constraints, and out-of-scope behavior.

## Branch

```text
feature/180/register-loss-expired-disposal
```

The branch starts from current `develop` after #179 and targets `develop` through the normal issue PR flow.

## Existing behavior to preserve

The implementation must preserve these established contracts and invariants:

- `inventory` owns `Batch`, stock balances, movement creation, locking, and stock invariants;
- quantities use `BigDecimal` and PostgreSQL `NUMERIC(19,6)`;
- stock cannot become negative;
- `StockMovement.quantity` is always positive; direction/semantics come from `MovementType`;
- stock movements are immutable audit records;
- `Batch.removeQuantity(...)` already enforces positive exact quantities and insufficient-stock protection;
- selected-batch write operations acquire `BatchRepository.lockByIdForUpdate(batchId)` before loading/mutating the balance;
- balance mutation and movement persistence occur inside one `@Transactional` application use case;
- `Clock` is the application time source;
- `expiresAt <= today` means expired;
- exact `BigDecimal` HTTP response values serialize as plain decimal JSON strings;
- canonical Angular/frontend quantity requests use decimal strings, while existing Jackson-compatible numeric requests remain supported;
- standard API errors flow through `DomainException` / `ApiErrorResponse` and the global exception handler;
- the movement-history read model already supports `LOSS` and `EXPIRED_DISPOSAL` values because they are existing `MovementType` members;
- no schema change is required merely to persist these movement values because `stock_movement.movement_type` is an existing string column.

Do not redesign these behaviors in #180.

## Approved HTTP contracts

Add two focused routes to the existing inventory movement HTTP boundary:

```http
POST /api/v1/inventory/batches/{batchId}/losses
POST /api/v1/inventory/batches/{batchId}/expired-disposals
```

Do not add a generic route that accepts a caller-provided `MovementType`.

Both routes return `201 Created` on success.

### Loss request

Canonical request:

```json
{
  "quantity": "12.500000",
  "reason": "Bottle broke during handling"
}
```

### Expired-disposal request

Canonical request:

```json
{
  "quantity": "20.000000",
  "reason": "Expired stock physically discarded"
}
```

### Request validation

Both request DTOs must require:

- `quantity` present;
- `quantity > 0`;
- at most 13 integer digits and 6 fractional digits, matching `NUMERIC(19,6)`;
- `reason` present and non-blank;
- `reason` maximum length 255.

Use the existing Bean Validation conventions at the HTTP boundary. Application/domain code must still defend the business operation when invoked outside MVC; do not rely exclusively on controller validation.

Do not convert quantities to `double` or `float`.

### Success response

Follow the existing stock-movement response shape:

```json
{
  "movementId": "<uuid>",
  "batchId": "<uuid>",
  "type": "LOSS",
  "quantity": "12.500000",
  "resultingBalance": "87.500000",
  "reason": "Bottle broke during handling",
  "occurredAt": "<instant>"
}
```

Expired disposal uses `type: "EXPIRED_DISPOSAL"`.

Use the existing `StockMovementResult` application result. Prefer dedicated HTTP request/response DTOs for the two business operations, consistent with the existing entry/withdrawal/adjustment HTTP surface, rather than renaming or generalizing existing public contracts.

The global exact-decimal serializer remains authoritative for `quantity` and `resultingBalance`; do not add per-field numeric/string workarounds.

## Application use cases

Add focused application services under the existing inventory movement capability, for example:

```text
inventory/application/movement/
├── RegisterStockLoss.java
├── RegisterStockLossCommand.java
├── RegisterExpiredStockDisposal.java
└── RegisterExpiredStockDisposalCommand.java
```

Exact names may vary only if existing package conventions require a minor adjustment.

### Register stock loss

Required execution semantics:

1. validate the mandatory audit reason defensively;
2. acquire the existing exclusive batch write lock;
3. load the batch or fail with the existing `BATCH_NOT_FOUND` behavior;
4. call `Batch.removeQuantity(quantity)`;
5. create an immutable `StockMovement` with `MovementType.LOSS` and the positive quantity;
6. use `Instant.now(clock)` for `occurredAt`;
7. persist the batch and movement within the same transaction;
8. return `StockMovementResult` with the resulting balance.

Loss is an explicit physical event. It is allowed for an existing batch regardless of whether the batch has an expiration date or is already expired. Do not infer or rewrite it as `ADJUSTMENT_OUT` or `EXPIRED_DISPOSAL`.

### Register expired-stock disposal

Required execution semantics:

1. validate the mandatory audit reason defensively;
2. acquire the existing exclusive batch write lock;
3. load the batch or fail with the existing `BATCH_NOT_FOUND` behavior;
4. compute `LocalDate.now(clock)` as the current business date;
5. verify the selected batch is expired;
6. reject when `expiresAt == null`;
7. reject when `expiresAt` is after the current business date;
8. accept when `expiresAt` is equal to or before the current business date;
9. call `Batch.removeQuantity(quantity)`;
10. create an immutable `StockMovement` with `MovementType.EXPIRED_DISPOSAL` and the positive quantity;
11. use `Instant.now(clock)` for `occurredAt`;
12. persist batch and movement atomically;
13. return `StockMovementResult` with the resulting balance.

The expiration rule is backend-authoritative. Do not derive disposal eligibility from an alert DTO, frontend state, FEFO result, or another module.

## Expired-disposal rejection

Introduce one inventory-domain/application-visible business-rule error for a selected batch that is not eligible for expired disposal.

Approved stable API code:

```text
BATCH_NOT_EXPIRED
```

The error must use the project-standard `DomainException` contract with `ErrorType.BUSINESS_RULE`, therefore mapping to HTTP `422 Unprocessable Content` through the existing global handler.

Include the `batchId` in safe error details following current inventory exception conventions.

Use this error for both:

- `expiresAt == null`;
- `expiresAt > businessDate`.

Do not reuse `EXPIRED_BATCH`; that existing error means an operation attempted to consume a batch that *is* expired, which is the inverse business condition.

## Mandatory audit reason

Both new operations require an explicit reason because the event must be explainable in movement history.

Requirements:

- HTTP requests reject null, missing, empty, or whitespace-only reasons;
- maximum persisted length remains 255;
- application use cases reject blank reasons defensively before mutating stock;
- persisted movement reason follows existing `StockMovement` normalization;
- do not change `StockMovement` globally to require a reason, because existing movement types have different established contracts;
- do not change existing withdrawal/entry reason semantics in #180.

Use the smallest existing-style validation/error approach. Do not introduce a broad new validation framework or a generic movement-command hierarchy.

## Locking and transaction boundary

Both operations must follow the selected-batch mutation sequence already established by withdrawal/adjustment:

```text
@Transactional use case
  -> lockByIdForUpdate(batchId)
  -> findById(batchId)
  -> validate operation-specific eligibility
  -> mutate Batch
  -> create StockMovement
  -> save Batch
  -> save StockMovement
  -> commit
```

The lock must cover the balance read, eligibility check, balance mutation, and movement persistence.

Do not introduce optimistic in-memory balance updates, a second stock engine, a separate transaction, or a repository implementation bypass.

If movement persistence fails, the batch balance mutation must roll back.

## Domain model changes

Keep domain changes minimal.

Expected:

- reuse `Batch.removeQuantity(...)` for both operations;
- reuse existing `MovementType.LOSS` and `MovementType.EXPIRED_DISPOSAL`;
- reuse `StockMovement.create(...)`;
- add only the business-rule exception needed for non-expired disposal if no equivalent correct exception already exists.

Do not:

- add new movement enum values;
- change the sign convention of `StockMovement.quantity`;
- add loss/disposal flags to `Batch`;
- store a disposed/expired boolean derived from dates;
- rewrite existing movement records;
- add a generic `applyMovement(MovementType, ...)` public API solely for these two use cases.

## Persistence and schema

No Flyway migration is expected for #180.

The existing schema already stores:

- batch balances with exact decimal quantities;
- arbitrary existing `MovementType` string values;
- positive movement quantities;
- reason and audit instant.

If implementation discovers a real persistence constraint that prevents `LOSS` or `EXPIRED_DISPOSAL`, stop and report it before changing schema. Do not add a migration speculatively.

## HTTP/OpenAPI behavior

Extend the existing `StockMovementController` rather than creating an inventory-wide parallel controller unless current repository conventions provide a stronger focused boundary.

Document both operations with OpenAPI summaries/descriptions and principal error cases.

### `POST .../losses`

Document at least:

- `201` success;
- `400` invalid request quantity/reason;
- `404` batch not found;
- `422` insufficient stock.

### `POST .../expired-disposals`

Document at least:

- `201` success;
- `400` invalid request quantity/reason;
- `404` batch not found;
- `422` insufficient stock or `BATCH_NOT_EXPIRED`.

Do not change existing endpoint routes or response contracts.

## Test requirements

Add focused tests at the lowest useful layer plus PostgreSQL/Testcontainers integration coverage for the transactional behavior.

### Application/domain tests

Cover loss:

- successful loss produces `LOSS`;
- quantity remains positive in the movement;
- exact-zero resulting balance is valid;
- insufficient stock fails without producing a movement;
- invalid/non-positive quantity is rejected by existing quantity rules;
- blank reason is rejected;
- batch lock is acquired before balance mutation/loading according to existing use-case conventions;
- `occurredAt` uses the injected fixed `Clock`.

Cover expired disposal:

- successful disposal produces `EXPIRED_DISPOSAL`;
- expiration yesterday is accepted;
- expiration exactly on `LocalDate.now(clock)` is accepted;
- expiration tomorrow is rejected with `BATCH_NOT_EXPIRED`;
- null expiration is rejected with `BATCH_NOT_EXPIRED`;
- exact-zero resulting balance is valid;
- insufficient stock fails without producing a movement;
- invalid/non-positive quantity is rejected;
- blank reason is rejected;
- `occurredAt` and expiration eligibility use the injected `Clock`.

Do not use dates that depend on the real current date. Use a fixed `Clock` and dates relative to that controlled business date.

### PostgreSQL/Testcontainers integration tests

Cover at minimum:

- persisted loss decrements the batch and creates one immutable `LOSS` movement;
- persisted expired disposal decrements an expired batch and creates one immutable `EXPIRED_DISPOSAL` movement;
- exact-decimal values persist without rounding;
- exact-zero resulting balance persists correctly;
- insufficient stock leaves the original balance and movement count unchanged;
- non-expired/null-expiration rejection leaves balance/history unchanged;
- expiration boundary (`expiresAt == today`) succeeds;
- a forced downstream movement-persistence failure rolls back the batch balance mutation within the same transaction.

The rollback test must exercise the real PostgreSQL-backed transaction/batch persistence boundary. Use existing Spring test facilities or a test spy/failure injection around the movement persistence port if needed; do not add production hooks or weaken production code for the test.

Preserve existing concurrency tests. Add a new concurrent integration test only if the repository's current test harness makes this straightforward; at minimum prove the new use cases call the established pessimistic selected-batch lock path and keep all existing concurrency verification green.

### HTTP/controller tests

Cover:

- exact route and `201` response for both operations;
- canonical decimal-string request binding;
- representative exact-decimal response strings;
- `LOSS` and `EXPIRED_DISPOSAL` wire values;
- mandatory reason validation;
- zero/negative/over-scale quantity validation;
- `BATCH_NOT_FOUND` mapping;
- `INSUFFICIENT_STOCK` mapping;
- `BATCH_NOT_EXPIRED` mapping;
- CSRF/authenticated controller test conventions already established by #178/#179 security work.

### Regression verification

Existing tests for all of the following must remain meaningful and green:

- entry;
- signed adjustment;
- selected-batch withdrawal;
- FEFO withdrawal;
- production stock consumption/output;
- current stock;
- inventory alerts;
- movement history;
- exact-decimal HTTP serialization;
- Spring Modulith module verification.

Do not modify unrelated tests merely to make the suite pass.

## Documentation

Update current API documentation if the repository maintains the stock-operation route list there.

Do not create a new ADR. This issue adds two concrete use cases inside an already-approved inventory movement architecture and does not introduce a new architectural strategy.

Do not rewrite unrelated inventory/domain documentation.

## Out of scope

Explicitly excluded:

- automatic disposal at expiration time;
- scheduled jobs;
- notifications;
- frontend loss/disposal actions (#181 owns frontend stock maintenance);
- stock adjustment redesign;
- production reversal/cancellation;
- generic movement-type mutation endpoints;
- minimum-shelf-life policy #73;
- FEFO redesign;
- authentication/security changes;
- new dependencies;
- schema redesign.

## Expected implementation footprint

The smallest complete change is expected to touch only focused areas such as:

```text
backend/src/main/java/.../inventory/application/movement/
backend/src/main/java/.../inventory/domain/exception/
backend/src/main/java/.../inventory/infrastructure/web/StockMovementController.java
backend/src/main/java/.../inventory/infrastructure/web/request/
backend/src/main/java/.../inventory/infrastructure/web/response/
backend/src/test/java/.../inventory/
docs/development/api-documentation.md   # only if route documentation requires update
```

Do not treat this list as permission to modify every listed file. Change only what the implementation actually needs.

## Final validation

From `backend/`:

```bash
./mvnw verify
```

Before reporting completion, also review:

```bash
git diff --check
git diff
git status --short
```

The implementation is complete only when:

- all acceptance criteria in issue #180 are satisfied;
- new loss/disposal behavior is covered by focused and PostgreSQL-backed tests;
- exact-decimal HTTP behavior is preserved;
- no existing stock behavior changes unintentionally;
- Spring Modulith verification is green;
- `./mvnw verify` succeeds;
- there are no unrelated changes.
