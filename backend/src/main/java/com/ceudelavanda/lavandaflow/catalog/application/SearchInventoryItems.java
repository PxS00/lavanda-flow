package com.ceudelavanda.lavandaflow.catalog.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Searches inventory catalog items through framework-neutral filters and pagination. */
@Service
@RequiredArgsConstructor
public class SearchInventoryItems {

    private final InventoryItemQuery inventoryItemQuery;

    @Transactional(readOnly = true)
    public InventoryItemPage execute(InventoryItemSearchQuery query) {
        return inventoryItemQuery.search(query);
    }
}
