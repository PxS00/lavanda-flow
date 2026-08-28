package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.inventory.application.stock.CurrentStockResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CurrentStockResponse(
    UUID inventoryItemId,
    @Schema(description = "Current catalog activation state. Inactive items remain queryable.")
    boolean active,
    @Schema(description = "Total physical/accounting balance across all batches for the item, including expired and zero-balance batches.", example = "180.500000")
    BigDecimal totalCurrentQuantity,
    List<BatchStockResponse> batches
) {
    public CurrentStockResponse {
        batches = List.copyOf(batches);
    }

    public static CurrentStockResponse from(CurrentStockResult result) {
        return new CurrentStockResponse(
            result.inventoryItemId(), result.active(), result.totalCurrentQuantity(),
            result.batches().stream().map(BatchStockResponse::from).toList()
        );
    }
}
