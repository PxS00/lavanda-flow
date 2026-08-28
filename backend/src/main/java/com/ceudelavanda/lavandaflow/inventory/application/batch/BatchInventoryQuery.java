package com.ceudelavanda.lavandaflow.inventory.application.batch;

import java.util.List;
import java.util.UUID;

/**
 * Read port for operational batch inventory queries.
 */
public interface BatchInventoryQuery {

    List<BatchInventoryRecord> findByInventoryItemId(UUID inventoryItemId);
}
