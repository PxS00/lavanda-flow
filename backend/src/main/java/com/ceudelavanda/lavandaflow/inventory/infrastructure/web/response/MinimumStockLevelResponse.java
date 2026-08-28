package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.inventory.application.minimumstock.MinimumStockLevelResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

public record MinimumStockLevelResponse(
    UUID inventoryItemId,
    @Schema(description = "Configured positive minimum stock quantity with six decimal places.", example = "250.000000")
    BigDecimal minimumQuantity
) {
    public static MinimumStockLevelResponse from(MinimumStockLevelResult result) {
        return new MinimumStockLevelResponse(result.inventoryItemId(), result.minimumQuantity());
    }
}
