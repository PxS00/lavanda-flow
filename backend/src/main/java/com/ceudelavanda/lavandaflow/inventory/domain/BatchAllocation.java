package com.ceudelavanda.lavandaflow.inventory.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One positive quantity assigned to a batch by a FEFO allocation plan.
 */
public record BatchAllocation(UUID batchId, BigDecimal quantity) {
}
