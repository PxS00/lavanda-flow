package com.ceudelavanda.lavandaflow.production.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.production.domain.ProductionConsumption;
import com.ceudelavanda.lavandaflow.production.domain.ProductionExecution;

final class ProductionExecutionMapper {

    private ProductionExecutionMapper() {
    }

    static ProductionExecutionJpaEntity toEntity(ProductionExecution execution) {
        return new ProductionExecutionJpaEntity(
            execution.getId(),
            execution.getFormulaId(),
            execution.getOutputInventoryItemId(),
            execution.getOutputBatchId(),
            execution.getOutputQuantity(),
            execution.getLotCode(),
            execution.getLotCodeMode(),
            execution.getProductionDate(),
            execution.getOutputReceivedAt(),
            execution.getOutputExpiresAt(),
            execution.getCompletedAt(),
            execution.getConsumptions().stream()
                .map(consumption -> new ProductionConsumptionJpaValue(
                    consumption.sourceBatchId(),
                    consumption.sourceInventoryItemId(),
                    consumption.movementId(),
                    consumption.quantity()
                ))
                .toList()
        );
    }

    static ProductionExecution toDomain(ProductionExecutionJpaEntity entity) {
        return new ProductionExecution(
            entity.getId(),
            entity.getFormulaId(),
            entity.getOutputInventoryItemId(),
            entity.getOutputBatchId(),
            entity.getOutputQuantity(),
            entity.getLotCode(),
            entity.getLotCodeMode(),
            entity.getProductionDate(),
            entity.getOutputReceivedAt(),
            entity.getOutputExpiresAt(),
            entity.getCompletedAt(),
            entity.getConsumptions().stream()
                .map(consumption -> new ProductionConsumption(
                    consumption.getSourceBatchId(),
                    consumption.getSourceInventoryItemId(),
                    consumption.getMovementId(),
                    consumption.getQuantity()
                ))
                .toList()
        );
    }
}
