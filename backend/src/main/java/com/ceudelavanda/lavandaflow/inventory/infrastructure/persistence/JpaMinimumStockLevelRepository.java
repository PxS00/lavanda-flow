package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.inventory.domain.MinimumStockLevel;
import com.ceudelavanda.lavandaflow.inventory.domain.MinimumStockLevelRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaMinimumStockLevelRepository implements MinimumStockLevelRepository {

    private final SpringDataMinimumStockLevelRepository repository;

    JpaMinimumStockLevelRepository(SpringDataMinimumStockLevelRepository repository) {
        this.repository = repository;
    }

    @Override
    public MinimumStockLevel save(MinimumStockLevel level) {
        var entity = repository.findById(level.getInventoryItemId())
            .map(existing -> {
                existing.changeMinimumQuantity(level.getMinimumQuantity());
                return existing;
            })
            .orElseGet(() -> MinimumStockLevelMapper.toEntity(level));

        return MinimumStockLevelMapper.toDomain(repository.save(entity));
    }

    @Override
    public Optional<MinimumStockLevel> findByInventoryItemId(UUID inventoryItemId) {
        return repository.findById(inventoryItemId)
            .map(MinimumStockLevelMapper::toDomain);
    }

    @Override
    public List<MinimumStockLevel> findAll() {
        return repository.findAll().stream()
            .map(MinimumStockLevelMapper::toDomain)
            .toList();
    }

    @Override
    public void deleteByInventoryItemId(UUID inventoryItemId) {
        repository.deleteById(inventoryItemId);
    }
}
