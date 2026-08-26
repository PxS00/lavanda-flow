package com.ceudelavanda.lavandaflow.catalog;

import java.util.Optional;
import java.util.UUID;

/**
 * Public catalog query contract for consumers that need to verify an inventory
 * item's existence and activation state without accessing catalog internals.
 */
public interface InventoryItemLookup {

    Optional<InventoryItemSnapshot> findById(UUID inventoryItemId);
}
