package com.ceudelavanda.lavandaflow.inventory;

import java.util.Optional;
import java.util.UUID;

/** Minimal public batch identity lookup used by production formula validation. */
public interface ProductionBatchReferenceLookup {

    Optional<ProductionBatchReference> findByBatchId(UUID batchId);
}
