package com.ceudelavanda.lavandaflow.inventory.application.overview;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Inventory-owned aggregate metrics required by the operational item overview. */
public record InventoryItemOverviewMetrics(
    BigDecimal totalCurrentQuantity,
    BigDecimal availableQuantity,
    BigDecimal minimumQuantity,
    long nonZeroBatchCount,
    LocalDate nearestExpiration,
    long expiredBatchCount,
    long expiringSoonBatchCount
) {
}
