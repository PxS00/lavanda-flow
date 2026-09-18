package com.ceudelavanda.lavandaflow.inventory.application.overview;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InventoryStockListResult(
    List<Entry> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    LocalDate asOfDate,
    int expirationWindowDays
) {
    public InventoryStockListResult {
        content = List.copyOf(content);
    }

    public record Entry(
        UUID inventoryItemId,
        String name,
        String category,
        UnitOfMeasure unitOfMeasure,
        boolean active,
        String essenceReference,
        String productionTypeCode,
        BigDecimal totalCurrentQuantity,
        BigDecimal availableQuantity,
        BigDecimal minimumQuantity,
        boolean lowStock,
        boolean outOfStock,
        long nonZeroBatchCount,
        LocalDate nearestExpiration
    ) {
    }
}
