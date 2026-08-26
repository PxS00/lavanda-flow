package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataBatchRepository extends JpaRepository<BatchJpaEntity, UUID> {

    List<BatchJpaEntity> findByInventoryItemId(UUID inventoryItemId);
}
