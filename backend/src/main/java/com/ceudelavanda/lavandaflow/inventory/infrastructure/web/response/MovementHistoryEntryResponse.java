package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.application.result.MovementHistoryEntryResult;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "One immutable inventory stock movement with item and batch context")
public record MovementHistoryEntryResponse(
    UUID movementId,
    UUID inventoryItemId,
    String inventoryItemName,
    UnitOfMeasure unitOfMeasure,
    boolean inventoryItemActive,
    UUID batchId,
    String lotCode,
    MovementType type,
    BigDecimal quantity,
    String reason,
    Instant occurredAt
) {

    public static MovementHistoryEntryResponse from(MovementHistoryEntryResult result) {
        return new MovementHistoryEntryResponse(
            result.movementId(),
            result.inventoryItemId(),
            result.inventoryItemName(),
            result.unitOfMeasure(),
            result.inventoryItemActive(),
            result.batchId(),
            result.lotCode(),
            result.type(),
            result.quantity(),
            result.reason(),
            result.occurredAt()
        );
    }
}
