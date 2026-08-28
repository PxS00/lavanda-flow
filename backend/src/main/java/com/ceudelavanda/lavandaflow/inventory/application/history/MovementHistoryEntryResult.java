package com.ceudelavanda.lavandaflow.inventory.application.history;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MovementHistoryEntryResult(
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
}
