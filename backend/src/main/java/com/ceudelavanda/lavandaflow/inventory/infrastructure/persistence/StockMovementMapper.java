package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.inventory.domain.StockMovement;

final class StockMovementMapper {

    private StockMovementMapper() {
    }

    static StockMovementJpaEntity toEntity(StockMovement movement) {
        return new StockMovementJpaEntity(
            movement.id(),
            movement.batchId(),
            movement.type(),
            movement.quantity(),
            movement.reason(),
            movement.occurredAt()
        );
    }

    static StockMovement toDomain(StockMovementJpaEntity entity) {
        return new StockMovement(
            entity.getId(),
            entity.getBatchId(),
            entity.getType(),
            entity.getQuantity(),
            entity.getReason(),
            entity.getOccurredAt()
        );
    }
}
