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

    /**
     * Acquires an exclusive write lock for one existing batch until the
     * surrounding transaction completes.
     *
     * <p>The lock protects subsequent balance reads and mutations from lost
     * updates. The regular {@link #findById(UUID)} method remains responsible
     * for loading the domain aggregate.</p>
     *
     * @param id batch identifier
     */
    void lockByIdForUpdate(UUID id);

    List<Batch> findByInventoryItemId(UUID inventoryItemId);

    /**
     * Acquires exclusive write locks for all existing batches of one inventory
     * item until the surrounding transaction completes.
     *
     * <p>Implementations must acquire multiple locks in a deterministic order
     * so FEFO operations cannot deadlock merely because rows were returned in
     * different orders.</p>
     *
     * @param inventoryItemId inventory item identifier
     */
    void lockByInventoryItemIdForUpdate(UUID inventoryItemId);

    List<Batch> findWithPositiveBalanceExpiringOnOrBefore(LocalDate expiresOnOrBefore);
}
