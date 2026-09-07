package com.ceudelavanda.lavandaflow.production.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataProductionExecutionRepository extends JpaRepository<ProductionExecutionJpaEntity, UUID> {
}
