package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.inventory.domain.StockMovement;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
class JpaStockMovementRepository implements StockMovementRepository {

    private final SpringDataStockMovementRepository repository;

    JpaStockMovementRepository(SpringDataStockMovementRepository repository) {
        this.repository = repository;
    }

    @Override
    public StockMovement save(StockMovement movement) {
        var entity = StockMovementMapper.toEntity(movement);
        var savedEntity = repository.save(entity);

        return StockMovementMapper.toDomain(savedEntity);
    }

    @Override
    public List<StockMovement> findByBatchIdOrderByOccurredAtAsc(UUID batchId) {
        return repository.findByBatchIdOrderByOccurredAtAsc(batchId).stream()
            .map(StockMovementMapper::toDomain)
            .toList();
    }
}
