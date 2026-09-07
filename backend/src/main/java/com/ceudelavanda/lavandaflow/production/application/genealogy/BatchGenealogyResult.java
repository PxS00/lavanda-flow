package com.ceudelavanda.lavandaflow.production.application.genealogy;

import java.util.List;

/** Recursive bidirectional genealogy rooted at one known inventory batch. */
public record BatchGenealogyResult(
    GenealogyDirection direction,
    GenealogyBatch rootBatch,
    List<GenealogyEdge> upstream,
    List<GenealogyEdge> downstream
) {
    public BatchGenealogyResult {
        upstream = List.copyOf(upstream);
        downstream = List.copyOf(downstream);
    }
}
