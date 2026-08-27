package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemLookup;
import com.ceudelavanda.lavandaflow.inventory.domain.MinimumStockLevelRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InventoryItemNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Removes a configured minimum stock level for an existing inventory item.
 *
 * <p>The deletion is idempotent when no configuration exists.</p>
 */
@Service
@RequiredArgsConstructor
public class DeleteMinimumStockLevel {

    private final InventoryItemLookup inventoryItemLookup;
    private final MinimumStockLevelRepository minimumStockLevelRepository;

    @Transactional
    public void execute(UUID inventoryItemId) {
        inventoryItemLookup.findById(inventoryItemId)
            .orElseThrow(() -> new InventoryItemNotFoundException(inventoryItemId));
        minimumStockLevelRepository.deleteByInventoryItemId(inventoryItemId);
    }
}
