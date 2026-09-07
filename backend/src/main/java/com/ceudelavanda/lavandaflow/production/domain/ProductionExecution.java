package com.ceudelavanda.lavandaflow.production.domain;

import com.ceudelavanda.lavandaflow.production.domain.exception.InvalidProductionExecutionException;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Immutable record of one completed internal production operation.
 *
 * <p>The execution owns historical production facts and references inventory batches and
 * movements only through stable identifiers. Stock balances remain inventory-owned.</p>
 */
@Getter
public final class ProductionExecution {

    private static final int MAX_LOT_CODE_LENGTH = 255;

    private final UUID id;
    private final UUID formulaId;
    private final UUID outputInventoryItemId;
    private final UUID outputBatchId;
    private final BigDecimal outputQuantity;
    private final String lotCode;
    private final ProductionLotCodeMode lotCodeMode;
    private final LocalDate productionDate;
    private final LocalDate outputReceivedAt;
    private final LocalDate outputExpiresAt;
    private final Instant completedAt;
    private final List<ProductionConsumption> consumptions;

    public ProductionExecution(
        UUID id,
        UUID formulaId,
        UUID outputInventoryItemId,
        UUID outputBatchId,
        BigDecimal outputQuantity,
        String lotCode,
        ProductionLotCodeMode lotCodeMode,
        LocalDate productionDate,
        LocalDate outputReceivedAt,
        LocalDate outputExpiresAt,
        Instant completedAt,
        List<ProductionConsumption> consumptions
    ) {
        this.id = requireNonNull(id, "id");
        this.formulaId = requireNonNull(formulaId, "formulaId");
        this.outputInventoryItemId = requireNonNull(outputInventoryItemId, "outputInventoryItemId");
        this.outputBatchId = requireNonNull(outputBatchId, "outputBatchId");
        this.outputQuantity = requireSupportedPositiveQuantity(outputQuantity, "outputQuantity");
        this.lotCode = requireLotCode(lotCode);
        this.lotCodeMode = requireNonNull(lotCodeMode, "lotCodeMode");
        this.productionDate = requireNonNull(productionDate, "productionDate");
        this.outputReceivedAt = requireNonNull(outputReceivedAt, "outputReceivedAt");
        this.outputExpiresAt = validateExpiration(outputExpiresAt, outputReceivedAt);
        this.completedAt = requireNonNull(completedAt, "completedAt");
        this.consumptions = requireConsumptions(consumptions);
    }

    public static ProductionExecution create(
        UUID formulaId,
        UUID outputInventoryItemId,
        UUID outputBatchId,
        BigDecimal outputQuantity,
        String lotCode,
        ProductionLotCodeMode lotCodeMode,
        LocalDate productionDate,
        LocalDate outputReceivedAt,
        LocalDate outputExpiresAt,
        Instant completedAt,
        List<ProductionConsumption> consumptions
    ) {
        return new ProductionExecution(
            UUID.randomUUID(),
            formulaId,
            outputInventoryItemId,
            outputBatchId,
            outputQuantity,
            lotCode,
            lotCodeMode,
            productionDate,
            outputReceivedAt,
            outputExpiresAt,
            completedAt,
            consumptions
        );
    }

    static BigDecimal requireSupportedPositiveQuantity(BigDecimal quantity, String field) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new InvalidProductionExecutionException(field, "Quantity must be greater than zero");
        }
        var fractionDigits = Math.max(quantity.scale(), 0);
        var integerDigits = Math.max(quantity.precision() - quantity.scale(), 0);
        if (integerDigits > 13 || fractionDigits > 6) {
            throw new InvalidProductionExecutionException(
                field,
                "Quantity must have at most 13 integer digits and 6 fractional digits"
            );
        }
        return quantity;
    }

    private static String requireLotCode(String lotCode) {
        if (lotCode == null || lotCode.trim().isEmpty()) {
            throw new InvalidProductionExecutionException("lotCode", "Internal production lot code must not be blank");
        }
        var normalized = lotCode.trim();
        if (normalized.length() > MAX_LOT_CODE_LENGTH) {
            throw new InvalidProductionExecutionException(
                "lotCode",
                "Internal production lot code must not exceed " + MAX_LOT_CODE_LENGTH + " characters"
            );
        }
        return normalized;
    }

    private static LocalDate validateExpiration(LocalDate expiresAt, LocalDate receivedAt) {
        if (expiresAt != null && expiresAt.isBefore(receivedAt)) {
            throw new InvalidProductionExecutionException(
                "outputExpiresAt",
                "Output expiration date must not be before output received date"
            );
        }
        return expiresAt;
    }

    private static List<ProductionConsumption> requireConsumptions(List<ProductionConsumption> consumptions) {
        if (consumptions == null || consumptions.isEmpty()) {
            throw new InvalidProductionExecutionException("consumptions", "Production must contain at least one consumption");
        }
        var sourceBatchIds = new HashSet<UUID>();
        for (var consumption : consumptions) {
            if (consumption == null) {
                throw new InvalidProductionExecutionException("consumptions", "Consumptions must not contain null entries");
            }
            if (!sourceBatchIds.add(consumption.sourceBatchId())) {
                throw new InvalidProductionExecutionException(
                    "consumptions",
                    "Production must not contain duplicate source batch consumptions"
                );
            }
        }
        return List.copyOf(consumptions);
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new InvalidProductionExecutionException(field, field + " must not be null");
        }
        return value;
    }
}
