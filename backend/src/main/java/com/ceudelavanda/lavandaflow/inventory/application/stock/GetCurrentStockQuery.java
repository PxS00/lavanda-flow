package com.ceudelavanda.lavandaflow.inventory.application.stock;

import java.util.UUID;

/**
 * Input for retrieving current stock for one inventory item.
 *
 * <p>When {@code includeZeroBalance} is false, zero-balance batches are
 * omitted from the detailed result only; the total always includes them.</p>
 */
public record GetCurrentStockQuery(
    UUID inventoryItemId,
    boolean includeZeroBalance
) {
}
