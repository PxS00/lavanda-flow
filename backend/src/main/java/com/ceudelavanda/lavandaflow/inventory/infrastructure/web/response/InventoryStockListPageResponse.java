package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.application.overview.InventoryStockListResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Paginated backend-authoritative operational stock summary")
public record InventoryStockListPageResponse(
    List<Entry> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    LocalDate asOfDate,
    int expirationWindowDays
) {
    public InventoryStockListPageResponse {
        content = List.copyOf(content);
    }

    public static InventoryStockListPageResponse from(InventoryStockListResult result) {
        return new InventoryStockListPageResponse(
            result.content().stream().map(Entry::from).toList(), result.page(), result.size(),
            result.totalElements(), result.totalPages(), result.asOfDate(), result.expirationWindowDays()
        );
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
        @Schema(nullable = true) BigDecimal minimumQuantity,
        boolean lowStock,
        boolean outOfStock,
        long nonZeroBatchCount,
        @Schema(nullable = true) LocalDate nearestExpiration
    ) {
        private static Entry from(InventoryStockListResult.Entry result) {
            return new Entry(
                result.inventoryItemId(), result.name(), result.category(), result.unitOfMeasure(), result.active(),
                result.essenceReference(), result.productionTypeCode(), result.totalCurrentQuantity(),
                result.availableQuantity(), result.minimumQuantity(), result.lowStock(), result.outOfStock(),
                result.nonZeroBatchCount(), result.nearestExpiration()
            );
        }
    }
}
