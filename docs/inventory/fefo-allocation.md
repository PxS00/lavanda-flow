# FEFO Allocation

## Purpose

Automatic withdrawals by inventory item use First Expired, First Out (FEFO) allocation across eligible batches.

```text
POST /api/v1/inventory/items/{inventoryItemId}/withdrawals
```

The explicit batch withdrawal endpoint remains available and unchanged:

```text
POST /api/v1/inventory/batches/{batchId}/withdrawals
```

## Eligibility

A batch is eligible only when all of the following are true:

- `currentQuantity > 0`;
- `expiresAt != null`;
- `expiresAt >` the current business date.

`expiresAt == today` is already expired. `expiresAt < today` is expired. A batch with `expiresAt == null` does not participate in automatic FEFO, and a zero-balance batch remains a historical record but is not allocated. An `InventoryItem` may exist with total stock zero.

Automatic FEFO is blocked for inactive inventory items. The business date comes from the configured `Clock` in `America/Sao_Paulo`; movement timestamps remain `Instant`-based.

## Ordering

Eligible batches are ordered deterministically:

1. `expiresAt ASC`
2. `currentQuantity ASC`
3. `receivedAt ASC`
4. `id ASC`

The earliest expiration wins. When expirations tie, the smaller remaining balance is consumed first to reduce fragmented stock, then the older receipt is used. UUID is only the final deterministic tie-breaker.

## Multi-batch allocation

One request can consume multiple batches. For a request of `80 ml` with A=`15 ml`, B=`25 ml`, and C=`100 ml`, allocation is A -> `15 ml`, B -> `25 ml`, C -> `40 ml`.

Each consumed batch produces its own immutable `CONSUMPTION` `StockMovement`.

## Atomicity

FEFO is all-or-nothing:

- the complete allocation plan is calculated before any `Batch` mutation;
- eligible quantity is summed first;
- when `requestedQuantity` exceeds eligible `availableQuantity`, no batch is modified;
- no partial withdrawal is automatically committed;
- the application use case is transactional;
- a persistence failure rolls back every batch and movement change.

Concurrency and locking are outside issue #22 and belong to issue #28.

## Insufficient stock

Insufficient eligible stock returns HTTP `422` with code `INSUFFICIENT_ELIGIBLE_STOCK`. Error `details` contains `inventoryItemId`, `requestedQuantity`, and `availableQuantity`; `availableQuantity` counts only FEFO-eligible stock.

The frontend may offer the available quantity, but accepting it requires a new explicit request; the backend never silently lowers `requestedQuantity`.

## Response

The success response contains:

- `inventoryItemId`;
- `requestedQuantity`;
- `allocatedQuantity`;
- `allocations[]`.

Each allocation contains `batchId`, `movementId`, and a positive `quantity`. On success, `requestedQuantity == allocatedQuantity`.

## Reason

`reason` is optional and limited to 255 characters. Blank values are normalized according to existing `StockMovement` behavior.

## Item state

A missing `InventoryItem` returns `INVENTORY_ITEM_NOT_FOUND` with HTTP `404`. An inactive item returns `INACTIVE_INVENTORY_ITEM` with HTTP `422`.

FEFO does not infer expiration behavior from `Category`.

## Scope exclusions

FEFO does not implement:

- partial-withdrawal flags;
- stock reservations;
- concurrency or locking;
- deletion of expired batches;
- deletion of zero-balance batches;
- category-based expiration rules;
- automatic fallback to batches without expiration dates.
