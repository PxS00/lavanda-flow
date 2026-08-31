package com.ceudelavanda.lavandaflow.inventory.application.batch;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Operational batch inventory result for one catalog item at a business date.
 */
public record BatchInventoryResult(
    UUID inventoryItemId,
    LocalDate asOfDate,
    List<BatchInventoryEntryResult> batches
) {
    public BatchInventoryResult {
        batches = List.copyOf(batches);
    }
}
