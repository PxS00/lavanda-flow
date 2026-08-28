package com.ceudelavanda.lavandaflow.inventory.application.fefo;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Result of a fully committed automatic FEFO withdrawal. */
public record FefoWithdrawalResult(
    UUID inventoryItemId,
    BigDecimal requestedQuantity,
    BigDecimal allocatedQuantity,
    List<FefoWithdrawalAllocationResult> allocations
) {
    public FefoWithdrawalResult {
        allocations = List.copyOf(allocations);
    }
}
