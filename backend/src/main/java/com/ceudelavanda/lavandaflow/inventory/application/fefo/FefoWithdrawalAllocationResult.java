package com.ceudelavanda.lavandaflow.inventory.application.fefo;

import java.math.BigDecimal;
import java.util.UUID;

/** Persisted movement generated for one batch allocation in a FEFO withdrawal. */
public record FefoWithdrawalAllocationResult(UUID batchId, UUID movementId, BigDecimal quantity) {
}
