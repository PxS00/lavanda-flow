package com.ceudelavanda.lavandaflow.production.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.production.application.history.ProductionExecutionHistoryResult;
import com.ceudelavanda.lavandaflow.production.domain.ProductionLotCodeMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProductionExecutionHistoryResponse(
    List<Entry> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public ProductionExecutionHistoryResponse {
        content = List.copyOf(content);
    }

    public static ProductionExecutionHistoryResponse from(ProductionExecutionHistoryResult result) {
        return new ProductionExecutionHistoryResponse(
            result.content().stream().map(Entry::from).toList(),
            result.page(), result.size(), result.totalElements(), result.totalPages()
        );
    }

    public record Entry(
        UUID executionId,
        UUID formulaId,
        UUID outputInventoryItemId,
        String outputItemName,
        UnitOfMeasure outputUnitOfMeasure,
        UUID outputBatchId,
        BigDecimal outputQuantity,
        String lotCode,
        ProductionLotCodeMode lotCodeMode,
        LocalDate productionDate,
        Instant completedAt
    ) {
        private static Entry from(ProductionExecutionHistoryResult.Entry result) {
            return new Entry(
                result.executionId(), result.formulaId(), result.outputInventoryItemId(),
                result.outputItemName(), result.outputUnitOfMeasure(), result.outputBatchId(),
                result.outputQuantity(), result.lotCode(), result.lotCodeMode(),
                result.productionDate(), result.completedAt()
            );
        }
    }
}
