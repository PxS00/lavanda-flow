package com.ceudelavanda.lavandaflow.inventory.application.alerts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

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
