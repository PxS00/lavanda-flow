package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.inventory.application.result.ExpirationAlertEntryResult;
import com.ceudelavanda.lavandaflow.inventory.application.result.ExpirationAlertStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExpirationAlertEntryResponse(
    @Schema(description = "Catalog inventory item identifier associated with the batch.")
    UUID inventoryItemId,
    @Schema(description = "Inventory batch identifier.")
    UUID batchId,
    @Schema(description = "Nullable supplier/manufacturer lot reference.", nullable = true, example = "ESS-LAV-042")
    String lotCode,
    @Schema(description = "Current positive materialized balance of the batch.", example = "25.500000")
    BigDecimal currentQuantity,
    @Schema(
        description = "Batch expiration date. A date equal to asOfDate is already considered expired.",
        example = "2026-08-26"
    )
    LocalDate expiresAt,
    @Schema(
        description = "Signed number of days from asOfDate to expiresAt. Negative values are past due, zero expires today, and positive values are future dates.",
        example = "-6"
    )
    long daysUntilExpiration,
    @Schema(
        description = "Expiration classification. EXPIRED includes expiresAt equal to asOfDate; EXPIRING_SOON applies only to future dates inside the alert window.",
        allowableValues = {"EXPIRED", "EXPIRING_SOON"},
        example = "EXPIRED"
    )
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
