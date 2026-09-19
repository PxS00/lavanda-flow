package com.ceudelavanda.lavandaflow.production.application.history;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.production.domain.ProductionLotCodeMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Complete persisted execution context enriched with current display metadata. */
public record ProductionExecutionDetailsResult(
    UUID executionId,
    UUID formulaId,
    UUID outputInventoryItemId,
    String outputItemName,
    UnitOfMeasure outputUnitOfMeasure,
    UUID outputBatchId,
    BigDecimal outputQuantity,
    String lotCode,
    ProductionLotCodeMode lotCodeMode,
    LocalDate productionDate,
    LocalDate outputReceivedAt,
    LocalDate outputExpiresAt,
    Instant completedAt,
    List<Consumption> consumptions
) {
    public ProductionExecutionDetailsResult {
        consumptions = List.copyOf(consumptions);
    }

    public record Consumption(
        UUID sourceBatchId,
        UUID sourceInventoryItemId,
        String sourceItemName,
        UnitOfMeasure sourceUnitOfMeasure,
        String sourceLotCode,
        UUID movementId,
        BigDecimal quantity
    ) {
    }
}
