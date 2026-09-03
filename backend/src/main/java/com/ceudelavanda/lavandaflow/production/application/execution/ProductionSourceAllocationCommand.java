package com.ceudelavanda.lavandaflow.production.application.execution;

import java.math.BigDecimal;
import java.util.UUID;

/** One exact source-batch quantity actually consumed by a completed production operation. */
public record ProductionSourceAllocationCommand(UUID batchId, BigDecimal quantity) {
}
