package com.ceudelavanda.lavandaflow.inventory.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Immutable, all-or-nothing withdrawal allocation calculated before any batch
 * state is changed.
 */
public record FefoAllocationPlan(
    UUID inventoryItemId,
    BigDecimal requestedQuantity,
    BigDecimal allocatedQuantity,
    List<BatchAllocation> allocations
) {

    public FefoAllocationPlan {
        allocations = List.copyOf(allocations);
    }
}
