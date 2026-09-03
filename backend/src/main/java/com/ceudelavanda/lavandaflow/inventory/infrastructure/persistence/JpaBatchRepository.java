package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaBatchRepository implements BatchRepository {

    private final SpringDataBatchRepository repository;

    JpaBatchRepository(SpringDataBatchRepository repository) {
        this.repository = repository;
    }

    @Override
    public Batch save(Batch batch) {
        var entity = BatchMapper.toEntity(batch);
        var savedEntity = repository.save(entity);

        return BatchMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Batch> findById(UUID id) {
        return repository.findById(id)
            .map(BatchMapper::toDomain);
    }

    @Override
    public Optional<UUID> findInventoryItemIdById(UUID id) {
        return repository.findInventoryItemIdById(id);
    }

    @Override
    public void lockByIdForUpdate(UUID id) {
        repository.findByIdForUpdate(id);
    }

    @Override
    public List<Batch> findByInventoryItemId(UUID inventoryItemId) {
        return repository.findByInventoryItemId(inventoryItemId).stream()
            .map(BatchMapper::toDomain)
            .toList();
    }

    @Override
    public void lockByInventoryItemIdForUpdate(UUID inventoryItemId) {
        repository.findByInventoryItemIdForUpdate(inventoryItemId);
    }

    @Override
    public List<Batch> findWithPositiveBalanceExpiringOnOrBefore(LocalDate expiresOnOrBefore) {
        return repository.findByExpiresAtLessThanEqualAndCurrentQuantityGreaterThan(
                expiresOnOrBefore,
                BigDecimal.ZERO
            ).stream()
            .map(BatchMapper::toDomain)
            .toList();
    }
}
