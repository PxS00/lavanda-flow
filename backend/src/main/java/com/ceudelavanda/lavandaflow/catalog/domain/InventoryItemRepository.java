package com.ceudelavanda.lavandaflow.catalog.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for inventory items managed by the catalog module.
 */
public interface InventoryItemRepository {

    InventoryItem save(InventoryItem item);

    Optional<InventoryItem> findById(UUID id);

    List<InventoryItem> findAllActive();

    List<InventoryItem> findByIds(Collection<UUID> ids);
}
