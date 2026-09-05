package com.ceudelavanda.lavandaflow.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public catalog query contract for consumers that need to verify an inventory
 * item's existence and activation state without accessing catalog internals.
 */
public interface InventoryItemLookup {

    /** Returns all active inventory items in one catalog read. */
    List<InventoryItemSnapshot> findAllActive();

    Optional<InventoryItemSnapshot> findById(UUID inventoryItemId);

    List<InventoryItemSnapshot> findByIds(Collection<UUID> inventoryItemIds);
}
