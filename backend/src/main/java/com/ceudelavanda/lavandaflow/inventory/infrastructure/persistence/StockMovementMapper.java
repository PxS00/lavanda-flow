package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.inventory.domain.StockMovement;

final class StockMovementMapper {

    private StockMovementMapper() {
    }

    static StockMovementJpaEntity toEntity(StockMovement movement) {
        var entity = new StockMovementJpaEntity(
            movement.id(),
            movement.batchId(),
            movement.type(),
            movement.quantity(),
            movement.reason(),
            movement.occurredAt()
        );
        entity.reference(movement.reference());
        return entity;
    }

    static StockMovement toDomain(StockMovementJpaEntity entity) {
        return new StockMovement(
            entity.getId(),
            entity.getBatchId(),
            entity.getType(),
            entity.getQuantity(),
            entity.getReason(),
            entity.getOccurredAt(), entity.reference()
        );
    }
}
