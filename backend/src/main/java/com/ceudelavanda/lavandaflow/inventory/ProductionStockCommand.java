package com.ceudelavanda.lavandaflow.inventory;

import java.util.List;

/** Exact source allocations and output data for one inventory-owned production stock operation. */
public record ProductionStockCommand(
    List<ProductionSourceAllocation> sourceAllocations,
    ProductionOutputBatch outputBatch
) {

    public ProductionStockCommand {
        sourceAllocations = sourceAllocations == null ? null : List.copyOf(sourceAllocations);
    }
}
