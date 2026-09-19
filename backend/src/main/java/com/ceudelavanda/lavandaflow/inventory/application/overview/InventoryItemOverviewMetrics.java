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
    private static final BigDecimal ZERO = new BigDecimal("0.000000");

    public static InventoryItemOverviewMetrics zero(BigDecimal minimumQuantity) {
        return new InventoryItemOverviewMetrics(ZERO, ZERO, minimumQuantity, 0, null, 0, 0);
    }

    public boolean lowStock(boolean active) {
        return active && minimumQuantity != null && availableQuantity.compareTo(minimumQuantity) < 0;
    }

    public boolean outOfStock() {
        return availableQuantity.signum() == 0;
    }
}
