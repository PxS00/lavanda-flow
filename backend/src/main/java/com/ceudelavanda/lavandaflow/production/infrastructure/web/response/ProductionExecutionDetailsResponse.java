package com.ceudelavanda.lavandaflow.production.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.production.application.history.ProductionExecutionDetailsResult;
import com.ceudelavanda.lavandaflow.production.domain.ProductionLotCodeMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProductionExecutionDetailsResponse(
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
    public ProductionExecutionDetailsResponse {
        consumptions = List.copyOf(consumptions);
    }

    public static ProductionExecutionDetailsResponse from(ProductionExecutionDetailsResult result) {
        return new ProductionExecutionDetailsResponse(
            result.executionId(), result.formulaId(), result.outputInventoryItemId(),
            result.outputItemName(), result.outputUnitOfMeasure(), result.outputBatchId(),
            result.outputQuantity(), result.lotCode(), result.lotCodeMode(),
            result.productionDate(), result.outputReceivedAt(), result.outputExpiresAt(),
            result.completedAt(), result.consumptions().stream().map(Consumption::from).toList()
        );
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
        private static Consumption from(ProductionExecutionDetailsResult.Consumption result) {
            return new Consumption(
                result.sourceBatchId(), result.sourceInventoryItemId(), result.sourceItemName(),
                result.sourceUnitOfMeasure(), result.sourceLotCode(), result.movementId(),
                result.quantity()
            );
        }
    }
}
