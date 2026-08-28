package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.inventory.application.stock.BatchStockResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BatchStockResponse(
    UUID batchId,
    UUID supplierId,
    String lotCode,
    @Schema(description = "Current materialized balance of the individual batch.", example = "30.500000")
    BigDecimal currentQuantity,
    LocalDate receivedAt,
    @Schema(description = "Nullable expiration date. The current-stock query does not classify expiration state.", nullable = true, example = "2026-09-15")
    LocalDate expiresAt
) {
    public static BatchStockResponse from(BatchStockResult result) {
        return new BatchStockResponse(
            result.batchId(), result.supplierId(), result.lotCode(), result.currentQuantity(), result.receivedAt(), result.expiresAt()
        );
    }
}
