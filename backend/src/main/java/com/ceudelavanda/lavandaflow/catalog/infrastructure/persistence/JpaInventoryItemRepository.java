package com.ceudelavanda.lavandaflow.catalog.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.catalog.application.InvalidInventoryItemMaintenanceException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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
    public boolean existsAny() {
        return repository.count() > 0;
    }

    @Override
    public InventoryItem save(InventoryItem item) {
        try {
            var entity = InventoryItemMapper.toEntity(item);
            var savedEntity = repository.saveAndFlush(entity);

            return InventoryItemMapper.toDomain(savedEntity);
        } catch (DataIntegrityViolationException exception) {
            if (hasCanonicalEssenceReferenceConflict(exception)) {
                throw InvalidInventoryItemMaintenanceException.canonicalEssenceReferenceConflict();
            }
            throw exception;
        }
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

    private static boolean hasCanonicalEssenceReferenceConflict(Throwable exception) {
        for (var cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraintViolation
                && "uq_inventory_item_essence_reference".equals(constraintViolation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
