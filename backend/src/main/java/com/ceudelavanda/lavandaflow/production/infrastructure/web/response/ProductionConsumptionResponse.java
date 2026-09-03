package com.ceudelavanda.lavandaflow.production.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.production.application.execution.RegisterProductionResult;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductionConsumptionResponse(
    UUID sourceBatchId,
    UUID sourceInventoryItemId,
    UUID movementId,
    BigDecimal quantity
) {

    static ProductionConsumptionResponse from(RegisterProductionResult.ConsumptionResult result) {
        return new ProductionConsumptionResponse(
            result.sourceBatchId(),
            result.sourceInventoryItemId(),
            result.movementId(),
            result.quantity()
        );
    }
}
