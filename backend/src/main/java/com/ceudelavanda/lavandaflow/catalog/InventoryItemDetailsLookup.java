package com.ceudelavanda.lavandaflow.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public catalog contract for read models that require item display details. */
public interface InventoryItemDetailsLookup {

    Optional<InventoryItemDetails> findById(UUID inventoryItemId);

    /**
     * Resolves display details for the supplied stable item identifiers in one module call.
     * Missing identifiers are omitted from the result.
     */
    List<InventoryItemDetails> findByIds(Collection<UUID> inventoryItemIds);
}
