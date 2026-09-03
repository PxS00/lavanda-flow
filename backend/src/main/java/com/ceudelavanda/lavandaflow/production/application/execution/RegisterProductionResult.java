package com.ceudelavanda.lavandaflow.production.application.execution;

import com.ceudelavanda.lavandaflow.production.domain.ProductionExecution;
import com.ceudelavanda.lavandaflow.production.domain.ProductionLotCodeMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Immutable result returned after one completed production execution commits its state. */
public record RegisterProductionResult(
    UUID executionId,
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
    List<ConsumptionResult> consumptions
) {

    public RegisterProductionResult {
        consumptions = List.copyOf(consumptions);
    }

    public static RegisterProductionResult from(ProductionExecution execution) {
        return new RegisterProductionResult(
            execution.getId(),
            execution.getFormulaId(),
            execution.getOutputInventoryItemId(),
            execution.getOutputBatchId(),
            execution.getOutputQuantity(),
            execution.getLotCode(),
            execution.getLotCodeMode(),
            execution.getProductionDate(),
            execution.getOutputReceivedAt(),
            execution.getOutputExpiresAt(),
            execution.getCompletedAt(),
            execution.getConsumptions().stream()
                .map(consumption -> new ConsumptionResult(
                    consumption.sourceBatchId(),
                    consumption.sourceInventoryItemId(),
                    consumption.movementId(),
                    consumption.quantity()
                ))
                .toList()
        );
    }

    public record ConsumptionResult(
        UUID sourceBatchId,
        UUID sourceInventoryItemId,
        UUID movementId,
        BigDecimal quantity
    ) {
    }
}
