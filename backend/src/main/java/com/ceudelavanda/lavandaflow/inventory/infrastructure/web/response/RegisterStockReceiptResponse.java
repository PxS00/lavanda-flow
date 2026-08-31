package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.inventory.application.receipt.StockReceiptResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Public response for one committed stock receipt. */
public record RegisterStockReceiptResponse(
    UUID batchId,
    UUID movementId,
    UUID inventoryItemId,
    @Schema(nullable = true) UUID supplierId,
    @Schema(nullable = true) String lotCode,
    @Schema(description = "Initial and current batch quantity with NUMERIC(19,6) semantics.") BigDecimal quantity,
    LocalDate receivedAt,
    @Schema(nullable = true) LocalDate expiresAt,
    @Schema(nullable = true) String reason,
    Instant occurredAt
) {
    public static RegisterStockReceiptResponse from(StockReceiptResult result) {
        return new RegisterStockReceiptResponse(
            result.batchId(),
            result.movementId(),
            result.inventoryItemId(),
            result.supplierId(),
            result.lotCode(),
            result.quantity(),
            result.receivedAt(),
            result.expiresAt(),
            result.reason(),
            result.occurredAt()
        );
    }
}
