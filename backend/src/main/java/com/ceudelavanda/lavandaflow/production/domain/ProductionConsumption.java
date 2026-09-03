package com.ceudelavanda.lavandaflow.production.domain;

import com.ceudelavanda.lavandaflow.production.domain.exception.InvalidProductionExecutionException;

import java.math.BigDecimal;
import java.util.UUID;

/** Immutable record of one exact source-batch quantity consumed by a production execution. */
public record ProductionConsumption(
    UUID sourceBatchId,
    UUID sourceInventoryItemId,
    UUID movementId,
    BigDecimal quantity
) {

    public ProductionConsumption {
        if (sourceBatchId == null) {
            throw new InvalidProductionExecutionException("consumption.sourceBatchId", "Source batch id must not be null");
        }
        if (sourceInventoryItemId == null) {
            throw new InvalidProductionExecutionException("consumption.sourceInventoryItemId", "Source inventory item id must not be null");
        }
        if (movementId == null) {
            throw new InvalidProductionExecutionException("consumption.movementId", "Consumption movement id must not be null");
        }
        ProductionExecution.requireSupportedPositiveQuantity(quantity, "consumption.quantity");
    }
}
