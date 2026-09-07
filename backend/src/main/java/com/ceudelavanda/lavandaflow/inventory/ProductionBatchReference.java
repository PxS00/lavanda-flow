package com.ceudelavanda.lavandaflow.inventory;

import java.util.UUID;

/** Immutable inventory batch identity needed by production. */
public record ProductionBatchReference(UUID batchId, UUID inventoryItemId) {
}
