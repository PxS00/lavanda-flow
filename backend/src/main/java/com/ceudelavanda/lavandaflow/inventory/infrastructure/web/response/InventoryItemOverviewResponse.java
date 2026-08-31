package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.application.overview.InventoryItemOverviewResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Compact operational inventory overview for one catalog item")
public record InventoryItemOverviewResponse(
    UUID inventoryItemId,
    String name,
    String category,
    UnitOfMeasure unitOfMeasure,
    boolean active,
    LocalDate asOfDate,
    int expirationWindowDays,
    BigDecimal totalCurrentQuantity,
    BigDecimal availableQuantity,
    @Schema(nullable = true, description = "Configured minimum available quantity, or null when no minimum is configured")
    BigDecimal minimumQuantity,
    boolean lowStock,
    boolean outOfStock,
    long nonZeroBatchCount,
    @Schema(nullable = true, description = "Earliest future expiration among positive-balance batches")
    LocalDate nearestExpiration,
    long expiredBatchCount,
    long expiringSoonBatchCount
) {

    public static InventoryItemOverviewResponse from(InventoryItemOverviewResult result) {
        return new InventoryItemOverviewResponse(
            result.inventoryItemId(), result.name(), result.category(), result.unitOfMeasure(), result.active(),
            result.asOfDate(), result.expirationWindowDays(), result.totalCurrentQuantity(), result.availableQuantity(),
            result.minimumQuantity(), result.lowStock(), result.outOfStock(), result.nonZeroBatchCount(),
            result.nearestExpiration(), result.expiredBatchCount(), result.expiringSoonBatchCount()
        );
    }
}
