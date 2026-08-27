package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemLookup;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemSnapshot;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class CatalogInventoryItemLookup implements InventoryItemLookup {

    private final InventoryItemRepository inventoryItemRepository;

    @Override
    public Optional<InventoryItemSnapshot> findById(UUID inventoryItemId) {
        return inventoryItemRepository.findById(inventoryItemId)
            .map(this::toSnapshot);
    }

    @Override
    public List<InventoryItemSnapshot> findByIds(Collection<UUID> inventoryItemIds) {
        return inventoryItemRepository.findByIds(inventoryItemIds).stream()
            .map(this::toSnapshot)
            .toList();
    }

    private InventoryItemSnapshot toSnapshot(com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem item) {
        return new InventoryItemSnapshot(
            item.getId(),
            item.getName(),
            item.getUnitOfMeasure(),
            item.isActive()
        );
    }
}
