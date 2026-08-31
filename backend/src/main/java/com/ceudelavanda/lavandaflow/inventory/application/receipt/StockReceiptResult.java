package com.ceudelavanda.lavandaflow.inventory.application.receipt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Stable operational result of a committed stock receipt. */
public record StockReceiptResult(
    UUID batchId,
    UUID movementId,
    UUID inventoryItemId,
    UUID supplierId,
    String lotCode,
    BigDecimal quantity,
    LocalDate receivedAt,
    LocalDate expiresAt,
    String reason,
    Instant occurredAt
) {
}
