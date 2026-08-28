package com.ceudelavanda.lavandaflow.catalog.application;

import java.util.Optional;
import java.util.UUID;

/** Read port for inventory catalog item consultation. */
public interface InventoryItemQuery {

    Optional<InventoryItemResult> findById(UUID inventoryItemId);

    InventoryItemPage search(InventoryItemSearchQuery query);
}
