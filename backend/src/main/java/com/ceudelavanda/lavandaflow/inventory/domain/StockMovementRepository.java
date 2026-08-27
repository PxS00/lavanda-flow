package com.ceudelavanda.lavandaflow.inventory.domain;

import java.util.List;
import java.util.UUID;

/**
 * Persistence boundary for the immutable movement history of inventory batches.
 */
public interface StockMovementRepository {

    StockMovement save(StockMovement movement);

    /**
     * Returns a batch history from the earliest recorded event to the latest.
     */
    List<StockMovement> findByBatchIdOrderByOccurredAtAsc(UUID batchId);
}
