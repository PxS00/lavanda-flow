package com.ceudelavanda.lavandaflow.catalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataInventoryItemRepository
    extends JpaRepository<InventoryItemJpaEntity, UUID> {
}
