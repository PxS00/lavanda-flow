package com.ceudelavanda.lavandaflow.production.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.production.domain.ProductionExecution;
import com.ceudelavanda.lavandaflow.production.domain.ProductionExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaProductionExecutionRepository implements ProductionExecutionRepository {

    private final SpringDataProductionExecutionRepository repository;

    @Override
    public ProductionExecution save(ProductionExecution execution) {
        return ProductionExecutionMapper.toDomain(
            repository.save(ProductionExecutionMapper.toEntity(execution))
        );
    }
}
