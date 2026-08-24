package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.inventory.domain.Batch;

final class BatchMapper {

    private BatchMapper() {
    }

    static BatchJpaEntity toEntity(Batch batch) {
        return new BatchJpaEntity(
            batch.getId(),
            batch.getInventoryItemId(),
            batch.getSupplierId(),
            batch.getLotCode(),
            batch.getInitialQuantity(),
            batch.getCurrentQuantity(),
            batch.getReceivedAt(),
            batch.getExpiresAt()
        );
    }

    static Batch toDomain(BatchJpaEntity entity) {
        return new Batch(
            entity.getId(),
            entity.getInventoryItemId(),
            entity.getSupplierId(),
            entity.getLotCode(),
            entity.getInitialQuantity(),
            entity.getCurrentQuantity(),
            entity.getReceivedAt(),
            entity.getExpiresAt()
        );
    }
}
