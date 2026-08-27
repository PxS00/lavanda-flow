# Inventory Movement History

## Goal

Issue #25 exposes the immutable inventory movement audit trail as a paginated read model without changing the stock-movement domain model.

The query belongs to the `inventory` module. `StockMovement` remains associated with exactly one batch; item-level filtering is resolved through `inventory_batch.inventory_item_id` on the read side rather than by duplicating `inventory_item_id` into `stock_movement`.

## Architecture

```text
HTTP
  |
  v
GetMovementHistory
  |
  v
MovementHistoryQuery (application read port)
  |
  v
JpaMovementHistoryQuery
  |
  v
SpringDataStockMovementRepository
  |
  v
PostgreSQL
```

The existing `StockMovementRepository` remains the persistence boundary used by stock-changing use cases and batch-level audit access. Paginated history is intentionally isolated behind a dedicated application read port so Spring Data pagination types do not leak into the domain or application API.

Inventory persistence may join `stock_movement` to `inventory_batch` because both tables belong to the `inventory` module. Catalog details must not be read by joining `inventory_item` directly. Human-readable item context is enriched through the public `catalog.InventoryItemLookup` contract, preserving Spring Modulith boundaries.

## Alternatives considered

### Extend `StockMovementRepository` with pagination

Rejected. It would mix command/domain persistence with a reporting concern and would either leak Spring Data pagination types or require query-specific abstractions in the domain.

### Dedicated JDBC/native SQL read model

Not selected for V1. The query is expressible with the approved Spring Data JPA stack, and the project already uses application read ports backed by Spring Data projections. Introducing a second persistence style would add complexity without a concrete benefit.

### Cursor/keyset pagination

Deferred. Keyset pagination scales better for very deep histories, but V1 benefits more from simple page/size navigation and total counts. The API uses deterministic ordering so a future cursor contract can be introduced if data volume justifies it.

## Query contract

Endpoint:

```text
GET /api/v1/inventory/movements
```

Optional filters:

- `inventoryItemId`: movements from every batch belonging to the item;
- `batchId`: movements from one batch;
- `type`: exact `MovementType`;
- `from`: inclusive ISO-8601 instant;
- `to`: exclusive ISO-8601 instant.

Pagination:

- `page`: zero-based, default `0`;
- `size`: default `20`, minimum `1`, maximum `100`.

Ordering is fixed and deterministic:

```text
occurredAt DESC, movementId DESC
```

Clients cannot provide arbitrary sorting in V1. Audit history is intentionally presented newest-first with a stable UUID tie-breaker.

If both temporal bounds are supplied, `from` must be strictly earlier than `to`.

## Read model

Each entry includes:

- movement identifier;
- inventory item identifier and public catalog context;
- batch identifier and lot code;
- movement type;
- positive exact-decimal quantity;
- optional reason;
- occurrence instant.

Movement quantities remain positive, matching the domain invariant. Direction is represented by `MovementType`; the read API does not create a second signed-quantity representation.

The historical response does not expose a reconstructed `resultingBalance`. The current schema stores immutable movement deltas and the materialized current batch balance, but not the balance snapshot after every event. Reconstructing historical balances would be a separate reporting rule and is outside issue #25.

## Persistence and indexes

The query uses `stock_movement -> inventory_batch` for item filtering. The V1 indexes should support:

- batch history ordered by occurrence and stable tie-breaker;
- item-to-batch lookup;
- global movement history ordered by occurrence and stable tie-breaker.

No index is added for `movement_type` initially because it has low cardinality and no demonstrated standalone query pressure.

## Consistency

Movement history is read-only and never mutates or repairs audit events. Corrections remain new adjustment movements. Inactive catalog items remain visible in historical results because deactivation must not erase auditability.
