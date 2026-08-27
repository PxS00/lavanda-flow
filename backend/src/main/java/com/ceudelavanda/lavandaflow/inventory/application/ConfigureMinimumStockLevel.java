package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemLookup;
import com.ceudelavanda.lavandaflow.inventory.application.result.MinimumStockLevelResult;
import com.ceudelavanda.lavandaflow.inventory.application.result.MinimumStockLevelUpdateResult;
import com.ceudelavanda.lavandaflow.inventory.domain.MinimumStockLevel;
import com.ceudelavanda.lavandaflow.inventory.domain.MinimumStockLevelRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InventoryItemNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Creates or updates the configured minimum stock level for an inventory item.
 *
 * <p>Catalog existence is required, but inactive items may retain or receive a configuration for
 * historical and future operational use.</p>
 */
@Service
@RequiredArgsConstructor
public class ConfigureMinimumStockLevel {

    private final InventoryItemLookup inventoryItemLookup;
    private final MinimumStockLevelRepository minimumStockLevelRepository;

    @Transactional
    public MinimumStockLevelUpdateResult execute(UUID inventoryItemId, BigDecimal minimumQuantity) {
        inventoryItemLookup.findById(inventoryItemId)
            .orElseThrow(() -> new InventoryItemNotFoundException(inventoryItemId));

        var existingLevel = minimumStockLevelRepository.findByInventoryItemId(inventoryItemId);
        var level = existingLevel.orElseGet(() -> new MinimumStockLevel(inventoryItemId, minimumQuantity));
        if (existingLevel.isPresent()) {
            level.changeMinimumQuantity(minimumQuantity);
        }

        var saved = minimumStockLevelRepository.save(level);
        return new MinimumStockLevelUpdateResult(toResult(saved), existingLevel.isEmpty());
    }

    private static MinimumStockLevelResult toResult(MinimumStockLevel level) {
        return new MinimumStockLevelResult(level.getInventoryItemId(), level.getMinimumQuantity());
    }
}
