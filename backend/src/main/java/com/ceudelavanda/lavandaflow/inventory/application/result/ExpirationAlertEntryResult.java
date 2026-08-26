package com.ceudelavanda.lavandaflow.inventory.application.result;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Expiration alert details for one inventory batch with positive balance.
 */
public record ExpirationAlertEntryResult(
    UUID inventoryItemId,
    UUID batchId,
    String lotCode,
    BigDecimal currentQuantity,
    LocalDate expiresAt,
    long daysUntilExpiration,
    ExpirationAlertStatus status
) {
}
