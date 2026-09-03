package com.ceudelavanda.lavandaflow.inventory;

import java.math.BigDecimal;
import java.util.UUID;

/** One exact source-batch quantity selected by the production application. */
public record ProductionSourceAllocation(UUID batchId, BigDecimal quantity) {
}
