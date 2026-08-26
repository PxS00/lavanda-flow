package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.inventory.application.result.ExpirationAlertEntryResult;
import com.ceudelavanda.lavandaflow.inventory.application.result.ExpirationAlertStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExpirationAlertEntryResponse(
    UUID inventoryItemId,
    UUID batchId,
    String lotCode,
    BigDecimal currentQuantity,
    LocalDate expiresAt,
    long daysUntilExpiration,
    ExpirationAlertStatus status
) {
    static ExpirationAlertEntryResponse from(ExpirationAlertEntryResult result) {
        return new ExpirationAlertEntryResponse(
            result.inventoryItemId(),
            result.batchId(),
            result.lotCode(),
            result.currentQuantity(),
            result.expiresAt(),
            result.daysUntilExpiration(),
            result.status()
        );
    }
}
