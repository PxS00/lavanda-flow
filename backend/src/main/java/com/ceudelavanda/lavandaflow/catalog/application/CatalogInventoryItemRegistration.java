package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemRegistration;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemSnapshot;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CatalogInventoryItemRegistration implements InventoryItemRegistration {

    private final RegisterInventoryItem registerInventoryItem;

    @Override
    public InventoryItemSnapshot registerEssence(String name) {
        var item = registerInventoryItem.execute(new RegisterInventoryItemCommand(
            name, null, Category.ESSENCE, UnitOfMeasure.MILLILITER
        ));
        return new InventoryItemSnapshot(item.id(), item.name(), item.unitOfMeasure(), item.active());
    }
}
