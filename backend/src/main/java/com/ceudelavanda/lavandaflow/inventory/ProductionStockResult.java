package com.ceudelavanda.lavandaflow.inventory;

import java.util.List;
import java.util.UUID;

/** Immutable identifiers and exact quantities created by a production stock operation. */
public record ProductionStockResult(
    UUID outputBatchId,
    UUID outputMovementId,
    List<ProductionSourceConsumptionResult> sourceConsumptions
) {

    public ProductionStockResult {
        sourceConsumptions = List.copyOf(sourceConsumptions);
    }
}
