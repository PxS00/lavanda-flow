package com.ceudelavanda.lavandaflow.production.application.history;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.production.domain.ProductionLotCodeMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Framework-neutral paginated completed-production history. */
public record ProductionExecutionHistoryResult(
    List<Entry> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public ProductionExecutionHistoryResult {
        content = List.copyOf(content);
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
    }
}
