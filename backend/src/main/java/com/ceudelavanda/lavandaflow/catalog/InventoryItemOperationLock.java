package com.ceudelavanda.lavandaflow.catalog;

import java.util.Optional;
import java.util.UUID;

/**
 * Public catalog synchronization contract for operations that must serialize
 * writes by inventory item.
 *
 * <p>The lock is transaction-scoped. A competing operation for the same item
 * waits until the current transaction commits or rolls back, then receives a
 * fresh item snapshot. Different inventory items remain independent.</p>
 */
public interface InventoryItemOperationLock {

    Optional<InventoryItemSnapshot> lockById(UUID inventoryItemId);
}
