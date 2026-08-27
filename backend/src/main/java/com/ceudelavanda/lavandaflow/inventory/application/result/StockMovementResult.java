package com.ceudelavanda.lavandaflow.inventory.application.result;

import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockMovementResult(

    UUID movementId,
    UUID batchId,
    MovementType type,
    BigDecimal quantity,
    BigDecimal resultingBalance,
    String reason,
    Instant occurredAt
) {
}
