package com.ceudelavanda.lavandaflow.catalog.application;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.ceudelavanda.lavandaflow.catalog.domain.Category;

/** Read port for inventory catalog item consultation. */
public interface InventoryItemQuery {

    Optional<InventoryItemResult> findById(UUID inventoryItemId);

    InventoryItemPage search(InventoryItemSearchQuery query);

    InventoryItemPage findStockPage(Set<Category> categories, int page, int size);
}
