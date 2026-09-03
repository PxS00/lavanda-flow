package com.ceudelavanda.lavandaflow.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Data required to create one ordinary internally produced inventory batch. */
public record ProductionOutputBatch(
    UUID inventoryItemId,
    String lotCode,
    BigDecimal quantity,
    LocalDate receivedAt,
    LocalDate expiresAt
) {
}
