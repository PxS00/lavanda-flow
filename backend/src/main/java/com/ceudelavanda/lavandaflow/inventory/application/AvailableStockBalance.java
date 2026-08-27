package com.ceudelavanda.lavandaflow.inventory.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Aggregated available stock for one inventory item at a given business date.
 */
public record AvailableStockBalance(UUID inventoryItemId, BigDecimal availableQuantity) {
}
