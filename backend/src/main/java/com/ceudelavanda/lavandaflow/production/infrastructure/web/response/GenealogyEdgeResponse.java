package com.ceudelavanda.lavandaflow.production.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.production.application.genealogy.GenealogyEdge;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "One exact persisted source-batch consumption edge and its recursive continuation")
public record GenealogyEdgeResponse(
    UUID executionId,
    UUID formulaId,
    LocalDate productionDate,
    Instant completedAt,
    BigDecimal consumedQuantity,
    GenealogyBatchResponse sourceBatch,
    GenealogyBatchResponse outputBatch,
    List<GenealogyEdgeResponse> next
) {
    public GenealogyEdgeResponse {
        next = List.copyOf(next);
    }

    public static GenealogyEdgeResponse from(GenealogyEdge edge) {
        return new GenealogyEdgeResponse(
            edge.executionId(),
            edge.formulaId(),
            edge.productionDate(),
            edge.completedAt(),
            edge.consumedQuantity(),
            GenealogyBatchResponse.from(edge.sourceBatch()),
            GenealogyBatchResponse.from(edge.outputBatch()),
            edge.next().stream().map(GenealogyEdgeResponse::from).toList()
        );
    }
}
