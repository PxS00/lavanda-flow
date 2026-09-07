package com.ceudelavanda.lavandaflow.production.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.production.application.genealogy.BatchGenealogyResult;
import com.ceudelavanda.lavandaflow.production.application.genealogy.GenealogyDirection;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Recursive production genealogy rooted at one inventory batch")
public record BatchGenealogyResponse(
    GenealogyDirection direction,
    GenealogyBatchResponse rootBatch,
    List<GenealogyEdgeResponse> upstream,
    List<GenealogyEdgeResponse> downstream
) {
    public BatchGenealogyResponse {
        upstream = List.copyOf(upstream);
        downstream = List.copyOf(downstream);
    }

    public static BatchGenealogyResponse from(BatchGenealogyResult result) {
        return new BatchGenealogyResponse(
            result.direction(),
            GenealogyBatchResponse.from(result.rootBatch()),
            result.upstream().stream().map(GenealogyEdgeResponse::from).toList(),
            result.downstream().stream().map(GenealogyEdgeResponse::from).toList()
        );
    }
}
