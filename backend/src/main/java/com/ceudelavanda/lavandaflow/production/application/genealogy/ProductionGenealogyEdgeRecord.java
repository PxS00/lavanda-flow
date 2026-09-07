package com.ceudelavanda.lavandaflow.production.application.genealogy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Flat persistence projection for one exact production consumption relation. */
public record ProductionGenealogyEdgeRecord(
    UUID executionId,
    UUID formulaId,
    UUID sourceBatchId,
    UUID outputBatchId,
    BigDecimal consumedQuantity,
    LocalDate productionDate,
    Instant completedAt
) {
}
