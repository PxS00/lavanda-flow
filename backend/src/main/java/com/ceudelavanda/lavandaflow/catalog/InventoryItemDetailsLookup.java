package com.ceudelavanda.lavandaflow.catalog;

import java.util.Optional;
import java.util.UUID;

/** Public catalog contract for read models that require item display details. */
public interface InventoryItemDetailsLookup {

    Optional<InventoryItemDetails> findById(UUID inventoryItemId);
}
