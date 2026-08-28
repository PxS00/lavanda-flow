package com.ceudelavanda.lavandaflow.catalog.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Retrieves one inventory catalog item by identifier. */
@Service
@RequiredArgsConstructor
public class GetInventoryItem {

    private final InventoryItemQuery inventoryItemQuery;

    @Transactional(readOnly = true)
    public InventoryItemResult execute(UUID inventoryItemId) {
        return inventoryItemQuery.findById(inventoryItemId)
            .orElseThrow(() -> new InventoryItemNotFoundException(inventoryItemId));
    }
}
