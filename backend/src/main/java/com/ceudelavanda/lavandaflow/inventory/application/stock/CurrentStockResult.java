package com.ceudelavanda.lavandaflow.inventory.application.stock;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Immutable current-stock view for one inventory item.
 *
 * <p>The total reflects every batch balance, while the batch detail may omit
 * zero-balance batches according to the query input.</p>
 */
public record CurrentStockResult(
    UUID inventoryItemId,
    boolean active,
    BigDecimal totalCurrentQuantity,
    List<BatchStockResult> batches
) {
    public CurrentStockResult {
        batches = List.copyOf(batches);
    }
}
