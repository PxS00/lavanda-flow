package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.inventory.application.result.StockMovementResult;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RegisterStockEntryResponse(
    UUID movementId,
    UUID batchId,
    MovementType type,
    BigDecimal quantity,
    BigDecimal resultingBalance,
    String reason,
    Instant occurredAt
) {
    public static RegisterStockEntryResponse from(StockMovementResult result) {
        return new RegisterStockEntryResponse(
            result.movementId(),
            result.batchId(),
            result.type(),
            result.quantity(),
            result.resultingBalance(),
            result.reason(),
            result.occurredAt()
        );
    }
}
