package com.ceudelavanda.lavandaflow.production.application.genealogy;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Production-owned persistence boundary for explicit recursive genealogy relations. */
public interface ProductionGenealogyQueryRepository {

    List<ProductionGenealogyEdgeRecord> findUpstreamEdges(UUID batchId);

    List<ProductionGenealogyEdgeRecord> findDownstreamEdges(UUID batchId);

    Set<UUID> findProducedBatchIds(Collection<UUID> batchIds);
}
