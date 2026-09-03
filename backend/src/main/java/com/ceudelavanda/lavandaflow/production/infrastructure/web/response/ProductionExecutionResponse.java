package com.ceudelavanda.lavandaflow.production.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.production.application.execution.RegisterProductionResult;
import com.ceudelavanda.lavandaflow.production.domain.ProductionLotCodeMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProductionExecutionResponse(
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
    List<ProductionConsumptionResponse> consumptions
) {

    public static ProductionExecutionResponse from(RegisterProductionResult result) {
        return new ProductionExecutionResponse(
            result.executionId(),
            result.formulaId(),
            result.outputInventoryItemId(),
            result.outputBatchId(),
            result.outputQuantity(),
            result.lotCode(),
            result.lotCodeMode(),
            result.productionDate(),
            result.outputReceivedAt(),
            result.outputExpiresAt(),
            result.completedAt(),
            result.consumptions().stream()
                .map(ProductionConsumptionResponse::from)
                .toList()
        );
    }
}
