package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.application.alerts.LowStockAlertEntryResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

public record LowStockAlertEntryResponse(
    UUID inventoryItemId,
    String name,
    UnitOfMeasure unitOfMeasure,
    @Schema(description = "Available non-expired quantity at asOfDate.", example = "0.000000") BigDecimal availableQuantity,
    @Schema(description = "Configured positive minimum quantity.", example = "250.000000") BigDecimal minimumQuantity,
    @Schema(description = "Minimum quantity minus available quantity.", example = "250.000000") BigDecimal deficitQuantity
) {
    static LowStockAlertEntryResponse from(LowStockAlertEntryResult result) {
        return new LowStockAlertEntryResponse(
            result.inventoryItemId(), result.name(), result.unitOfMeasure(), result.availableQuantity(),
            result.minimumQuantity(), result.deficitQuantity()
        );
    }
}
