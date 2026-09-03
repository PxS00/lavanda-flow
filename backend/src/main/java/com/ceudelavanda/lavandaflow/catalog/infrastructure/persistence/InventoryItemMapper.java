package com.ceudelavanda.lavandaflow.catalog.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;

final class InventoryItemMapper {

    private InventoryItemMapper() {
    }

    static InventoryItemJpaEntity toEntity(InventoryItem item) {
        return new InventoryItemJpaEntity(
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

    static InventoryItem toDomain(InventoryItemJpaEntity entity) {
        return new InventoryItem(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getCategory(),
            entity.getUnitOfMeasure(),
            entity.isActive(),
            entity.getEssenceReference(),
            entity.getProductionTypeCode()
        );
    }
}
