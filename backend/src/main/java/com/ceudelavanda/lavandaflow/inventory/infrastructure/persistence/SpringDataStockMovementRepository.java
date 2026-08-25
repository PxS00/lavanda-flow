package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataStockMovementRepository
    extends JpaRepository<StockMovementJpaEntity, UUID> {

    List<StockMovementJpaEntity> findByBatchIdOrderByOccurredAtAsc(UUID batchId);
}
