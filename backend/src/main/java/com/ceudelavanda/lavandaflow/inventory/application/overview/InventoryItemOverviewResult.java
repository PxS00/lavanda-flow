package com.ceudelavanda.lavandaflow.inventory.application.overview;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Stable application result for one inventory item's operational overview. */
public record InventoryItemOverviewResult(
    UUID inventoryItemId,
    String name,
    String category,
    UnitOfMeasure unitOfMeasure,
    boolean active,
    LocalDate asOfDate,
    int expirationWindowDays,
    BigDecimal totalCurrentQuantity,
    BigDecimal availableQuantity,
    BigDecimal minimumQuantity,
    boolean lowStock,
    boolean outOfStock,
    long nonZeroBatchCount,
    LocalDate nearestExpiration,
    long expiredBatchCount,
    long expiringSoonBatchCount
) {
}
