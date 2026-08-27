package com.ceudelavanda.lavandaflow.inventory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for configured inventory minimum stock levels.
 */
public interface MinimumStockLevelRepository {

    MinimumStockLevel save(MinimumStockLevel level);

    Optional<MinimumStockLevel> findByInventoryItemId(UUID inventoryItemId);

    List<MinimumStockLevel> findAll();

    void deleteByInventoryItemId(UUID inventoryItemId);
}
