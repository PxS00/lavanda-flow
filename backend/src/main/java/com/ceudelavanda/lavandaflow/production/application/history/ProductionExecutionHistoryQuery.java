package com.ceudelavanda.lavandaflow.production.application.history;

import com.ceudelavanda.lavandaflow.production.domain.ProductionLotCodeMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Production-owned persistence port for immutable completed execution reads. */
public interface ProductionExecutionHistoryQuery {

    Page find(GetProductionExecutionHistoryQuery query);

    Optional<Detail> findById(UUID executionId);

    record Summary(
        UUID executionId,
        UUID formulaId,
        UUID outputInventoryItemId,
        UUID outputBatchId,
        BigDecimal outputQuantity,
        String lotCode,
        ProductionLotCodeMode lotCodeMode,
        LocalDate productionDate,
        Instant completedAt
    ) {
    }

    record Page(
        List<Summary> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public Page {
            content = List.copyOf(content);
        }
    }

    record Detail(
        UUID executionId,
        UUID formulaId,
        UUID outputInventoryItemId,
        UUID outputBatchId,
        BigDecimal outputQuantity,
        String lotCode,
        ProductionLotCodeMode lotCodeMode,
        LocalDate productionDate,
        LocalDate outputReceivedAt,
        LocalDate outputExpiresAt,
        Instant completedAt,
        List<Consumption> consumptions
    ) {
        public Detail {
            consumptions = List.copyOf(consumptions);
        }
    }

    record Consumption(
        UUID sourceBatchId,
        UUID sourceInventoryItemId,
        UUID movementId,
        BigDecimal quantity
    ) {
    }
}
