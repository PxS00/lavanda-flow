package com.ceudelavanda.lavandaflow.inventory.application.history;

import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Persistence-facing movement-history projection owned by the inventory application layer. */
public record MovementHistoryEntry(
    UUID movementId,
    UUID inventoryItemId,
    UUID batchId,
    String lotCode,
    MovementType type,
    BigDecimal quantity,
    String reason,
    Instant occurredAt
) {
}
