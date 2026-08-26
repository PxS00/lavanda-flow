package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface SpringDataBatchRepository extends JpaRepository<BatchJpaEntity, UUID> {

    List<BatchJpaEntity> findByInventoryItemId(UUID inventoryItemId);

    List<BatchJpaEntity> findByExpiresAtLessThanEqualAndCurrentQuantityGreaterThan(
        LocalDate expiresAt,
        BigDecimal currentQuantity
    );
}
