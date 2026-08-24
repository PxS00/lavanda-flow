package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataBatchRepository extends JpaRepository<BatchJpaEntity, UUID> {
}
