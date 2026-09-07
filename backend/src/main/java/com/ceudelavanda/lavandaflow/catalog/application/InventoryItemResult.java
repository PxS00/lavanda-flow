package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;

import java.util.UUID;

/** Stable application read model for one inventory catalog item. */
public record InventoryItemResult(
    UUID id,
    String name,
    String description,
    Category category,
    UnitOfMeasure unitOfMeasure,
    boolean active,
    String essenceReference,
    String productionTypeCode
) {
    public InventoryItemResult(
        UUID id,
        String name,
        String description,
        Category category,
        UnitOfMeasure unitOfMeasure,
        boolean active
    ) {
        this(id, name, description, category, unitOfMeasure, active, null, null);
    }

    public static InventoryItemResult from(InventoryItem item) {
        return new InventoryItemResult(
            item.getId(),
            item.getName(),
            item.getDescription(),
            item.getCategory(),
            item.getUnitOfMeasure(),
            item.isActive(),
            item.getEssenceReference(),
            item.getProductionTypeCode()
        );
    }
}
