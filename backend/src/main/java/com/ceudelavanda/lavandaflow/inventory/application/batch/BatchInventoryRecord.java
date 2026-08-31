package com.ceudelavanda.lavandaflow.inventory.application.batch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Framework-neutral persistence projection used by the batch inventory read port.
 */
public record BatchInventoryRecord(
    UUID batchId,
    UUID inventoryItemId,
    UUID supplierId,
    String lotCode,
    BigDecimal initialQuantity,
    BigDecimal currentQuantity,
    LocalDate receivedAt,
    LocalDate expiresAt
) {
}
