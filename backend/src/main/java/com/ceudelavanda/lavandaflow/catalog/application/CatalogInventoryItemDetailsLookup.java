package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemDetails;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemDetailsLookup;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class CatalogInventoryItemDetailsLookup implements InventoryItemDetailsLookup {

    private final InventoryItemRepository inventoryItemRepository;

    @Override
    public Optional<InventoryItemDetails> findById(UUID inventoryItemId) {
        return inventoryItemRepository.findById(inventoryItemId)
            .map(item -> new InventoryItemDetails(
                item.getId(),
                item.getName(),
                item.getCategory().name(),
                item.getUnitOfMeasure(),
                item.isActive()
            ));
    }
}
