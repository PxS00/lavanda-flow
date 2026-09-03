package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemDetails;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemDetailsLookup;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class CatalogInventoryItemDetailsLookup implements InventoryItemDetailsLookup {

    private final InventoryItemRepository inventoryItemRepository;

    @Override
    public Optional<InventoryItemDetails> findById(UUID inventoryItemId) {
        return inventoryItemRepository.findById(inventoryItemId)
            .map(CatalogInventoryItemDetailsLookup::toDetails);
    }

    @Override
    public List<InventoryItemDetails> findByIds(Collection<UUID> inventoryItemIds) {
        if (inventoryItemIds == null || inventoryItemIds.isEmpty()) {
            return List.of();
        }
        return inventoryItemRepository.findByIds(inventoryItemIds).stream()
            .map(CatalogInventoryItemDetailsLookup::toDetails)
            .toList();
    }

    private static InventoryItemDetails toDetails(InventoryItem item) {
        return new InventoryItemDetails(
            item.getId(),
            item.getName(),
            item.getCategory().name(),
            item.getUnitOfMeasure(),
            item.isActive()
        );
    }
}
