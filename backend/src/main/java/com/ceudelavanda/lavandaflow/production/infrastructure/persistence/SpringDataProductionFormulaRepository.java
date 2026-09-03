package com.ceudelavanda.lavandaflow.production.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataProductionFormulaRepository extends JpaRepository<ProductionFormulaJpaEntity, UUID> {

    List<ProductionFormulaJpaEntity> findAllByOrderByIdAsc();
}
