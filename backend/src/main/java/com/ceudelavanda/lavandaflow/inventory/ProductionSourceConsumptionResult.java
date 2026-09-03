package com.ceudelavanda.lavandaflow.inventory;

import java.math.BigDecimal;
import java.util.UUID;

/** Immutable source-consumption identifiers and quantity created by inventory. */
public record ProductionSourceConsumptionResult(UUID batchId, UUID movementId, BigDecimal quantity) {
}
