package com.ceudelavanda.lavandaflow.catalog.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemOperationLock;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** PostgreSQL/JPA implementation of the catalog item-level serialization point. */
@Component
@RequiredArgsConstructor
class JpaInventoryItemOperationLock implements InventoryItemOperationLock {

    private final SpringDataInventoryItemRepository inventoryItemRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<InventoryItemSnapshot> lockById(UUID inventoryItemId) {
        return inventoryItemRepository.findByIdForUpdate(inventoryItemId)
            .map(item -> new InventoryItemSnapshot(
                item.getId(),
                item.getName(),
                item.getUnitOfMeasure(),
                item.isActive()
            ));
    }
}
