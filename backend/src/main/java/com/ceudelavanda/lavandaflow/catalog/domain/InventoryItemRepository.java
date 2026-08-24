package com.ceudelavanda.lavandaflow.catalog.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for inventory items managed by the catalog module.
 */
public interface InventoryItemRepository {

    InventoryItem save(InventoryItem item);

    Optional<InventoryItem> findById(UUID id);
}
