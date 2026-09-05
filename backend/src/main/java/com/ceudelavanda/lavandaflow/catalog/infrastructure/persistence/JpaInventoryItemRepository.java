package com.ceudelavanda.lavandaflow.catalog.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
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

    @Override
    public List<InventoryItem> findAllActive() {
        return repository.findAllByActiveTrue().stream()
            .map(InventoryItemMapper::toDomain)
            .toList();
    }

    @Override
    public List<InventoryItem> findByIds(Collection<UUID> ids) {
        return repository.findAllById(ids).stream()
            .map(InventoryItemMapper::toDomain)
            .toList();
    }
}
