package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemLookup;
import com.ceudelavanda.lavandaflow.inventory.application.result.MinimumStockLevelResult;
import com.ceudelavanda.lavandaflow.inventory.domain.MinimumStockLevelRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InventoryItemNotFoundException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.MinimumStockLevelNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Retrieves the configured minimum stock level for an existing inventory item, including inactive items.
 */
@Service
@RequiredArgsConstructor
public class GetMinimumStockLevel {

    private final InventoryItemLookup inventoryItemLookup;
    private final MinimumStockLevelRepository minimumStockLevelRepository;

    @Transactional(readOnly = true)
    public MinimumStockLevelResult execute(UUID inventoryItemId) {
        inventoryItemLookup.findById(inventoryItemId)
            .orElseThrow(() -> new InventoryItemNotFoundException(inventoryItemId));

        var level = minimumStockLevelRepository.findByInventoryItemId(inventoryItemId)
            .orElseThrow(() -> new MinimumStockLevelNotFoundException(inventoryItemId));

        return new MinimumStockLevelResult(level.getInventoryItemId(), level.getMinimumQuantity());
    }
}
