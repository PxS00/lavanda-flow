package com.ceudelavanda.lavandaflow.catalog.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaInventoryItemRepository
    implements InventoryItemRepository {

    private final SpringDataInventoryItemRepository repository;

    JpaInventoryItemRepository(
        SpringDataInventoryItemRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public InventoryItem save(InventoryItem item) {
        var entity = InventoryItemMapper.toEntity(item);
        var savedEntity = repository.save(entity);

        return InventoryItemMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<InventoryItem> findById(UUID id) {
        return repository.findById(id)
            .map(InventoryItemMapper::toDomain);
    }
}
