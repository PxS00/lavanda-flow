package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.inventory.application.batch.BatchInventoryEntryResult;
import com.ceudelavanda.lavandaflow.inventory.application.batch.BatchOperationalStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BatchInventoryEntryResponse(
    UUID batchId,
    UUID inventoryItemId,
    @Schema(description = "Nullable supplier identifier associated with the received batch.", nullable = true)
    UUID supplierId,
    @Schema(description = "Nullable supplier/manufacturer lot reference.", nullable = true, example = "ESS-LAV-042")
    String lotCode,
    @Schema(description = "Original received quantity with NUMERIC(19,6) semantics.", example = "100.000000")
    BigDecimal initialQuantity,
    @Schema(description = "Current materialized balance with NUMERIC(19,6) semantics.", example = "30.500000")
    BigDecimal currentQuantity,
    LocalDate receivedAt,
    @Schema(description = "Nullable expiration date.", nullable = true, example = "2026-09-15")
    LocalDate expiresAt,
    @Schema(
        description = "Derived operational status. ZERO_BALANCE takes precedence; positive-balance batches expiring on or before asOfDate are EXPIRED; all remaining batches are AVAILABLE.",
        allowableValues = {"AVAILABLE", "EXPIRED", "ZERO_BALANCE"}
    )
    BatchOperationalStatus status
) {
    public static BatchInventoryEntryResponse from(BatchInventoryEntryResult result) {
        return new BatchInventoryEntryResponse(
            result.batchId(),
            result.inventoryItemId(),
            result.supplierId(),
            result.lotCode(),
            result.initialQuantity(),
            result.currentQuantity(),
            result.receivedAt(),
            result.expiresAt(),
            result.status()
        );
    }
}
