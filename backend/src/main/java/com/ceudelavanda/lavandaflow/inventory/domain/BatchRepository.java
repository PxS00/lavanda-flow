package com.ceudelavanda.lavandaflow.inventory.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for batches managed by the inventory module.
 */
public interface BatchRepository {

    Batch save(Batch batch);

    Optional<Batch> findById(UUID id);

    List<Batch> findByInventoryItemId(UUID inventoryItemId);

    List<Batch> findWithPositiveBalanceExpiringOnOrBefore(LocalDate expiresOnOrBefore);
}
