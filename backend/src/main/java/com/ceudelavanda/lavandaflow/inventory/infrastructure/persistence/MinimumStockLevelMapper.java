package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.inventory.domain.MinimumStockLevel;

final class MinimumStockLevelMapper {

    private MinimumStockLevelMapper() {
    }

    static MinimumStockLevelJpaEntity toEntity(MinimumStockLevel level) {
        return new MinimumStockLevelJpaEntity(level.getInventoryItemId(), level.getMinimumQuantity());
    }

    static MinimumStockLevel toDomain(MinimumStockLevelJpaEntity entity) {
        return new MinimumStockLevel(entity.getInventoryItemId(), entity.getMinimumQuantity());
    }
}
