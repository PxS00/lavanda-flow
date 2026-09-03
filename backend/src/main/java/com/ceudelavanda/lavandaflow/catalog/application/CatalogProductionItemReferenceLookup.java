package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.catalog.ProductionItemReference;
import com.ceudelavanda.lavandaflow.catalog.ProductionItemReferenceLookup;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class CatalogProductionItemReferenceLookup implements ProductionItemReferenceLookup {

    private final InventoryItemRepository inventoryItemRepository;

    @Override
    public Optional<ProductionItemReference> findByInventoryItemId(UUID inventoryItemId) {
        return inventoryItemRepository.findById(inventoryItemId)
            .map(item -> new ProductionItemReference(
                item.getId(),
                item.getUnitOfMeasure(),
                item.isActive(),
                item.getEssenceReference(),
                item.getProductionTypeCode()
            ));
    }
}
