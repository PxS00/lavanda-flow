package com.ceudelavanda.lavandaflow.inventory.application.batch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Operational batch inventory entry exposed by the inventory application layer.
 */
public record BatchInventoryEntryResult(
    UUID batchId,
    UUID inventoryItemId,
    UUID supplierId,
    String lotCode,
    BigDecimal initialQuantity,
    BigDecimal currentQuantity,
    LocalDate receivedAt,
    LocalDate expiresAt,
    BatchOperationalStatus status
) {
}
