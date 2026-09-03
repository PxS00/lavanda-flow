package com.ceudelavanda.lavandaflow.production.application.genealogy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** One persisted exact consumption edge from a source batch through an execution to its output batch. */
public record GenealogyEdge(
    UUID executionId,
    UUID formulaId,
    LocalDate productionDate,
    Instant completedAt,
    BigDecimal consumedQuantity,
    GenealogyBatch sourceBatch,
    GenealogyBatch outputBatch,
    List<GenealogyEdge> next
) {
    public GenealogyEdge {
        next = List.copyOf(next);
    }
}
