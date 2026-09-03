package com.ceudelavanda.lavandaflow.production.application.genealogy;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemDetails;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemDetailsLookup;
import com.ceudelavanda.lavandaflow.inventory.BatchDetails;
import com.ceudelavanda.lavandaflow.inventory.BatchDetailsLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Resolves explicit recursive genealogy without ORM graph traversal or stock mutation. */
@Service
@RequiredArgsConstructor
public class GetBatchGenealogy {

    private static final Comparator<ProductionGenealogyEdgeRecord> EDGE_ORDER = Comparator
        .comparing(ProductionGenealogyEdgeRecord::completedAt)
        .thenComparing(ProductionGenealogyEdgeRecord::executionId)
        .thenComparing(ProductionGenealogyEdgeRecord::sourceBatchId)
        .thenComparing(ProductionGenealogyEdgeRecord::outputBatchId);

    private final ProductionGenealogyQueryRepository genealogyRepository;
    private final BatchDetailsLookup batchDetailsLookup;
    private final InventoryItemDetailsLookup inventoryItemDetailsLookup;

    @Transactional(readOnly = true)
    public BatchGenealogyResult execute(UUID batchId, GenealogyDirection direction) {
        if (batchId == null) {
            throw new IllegalArgumentException("batchId must not be null");
        }
        if (direction == null) {
            throw new IllegalArgumentException("direction must not be null");
        }

        var upstreamFlat = direction.includesUpstream()
            ? sorted(genealogyRepository.findUpstreamEdges(batchId))
            : List.<ProductionGenealogyEdgeRecord>of();
        var downstreamFlat = direction.includesDownstream()
            ? sorted(genealogyRepository.findDownstreamEdges(batchId))
            : List.<ProductionGenealogyEdgeRecord>of();

        var batchIds = collectBatchIds(batchId, upstreamFlat, downstreamFlat);
        var batchDetails = indexBatchDetails(batchIds);
        if (!batchDetails.containsKey(batchId)) {
            throw new BatchGenealogyNotFoundException(batchId);
        }

        var itemDetails = indexItemDetails(batchDetails.values());
        var producedBatchIds = genealogyRepository.findProducedBatchIds(batchIds);
        var batchViews = buildBatchViews(batchDetails, itemDetails, producedBatchIds);

        var upstreamByOutput = indexByOutputBatch(upstreamFlat);
        var downstreamBySource = indexBySourceBatch(downstreamFlat);

        return new BatchGenealogyResult(
            direction,
            batchViews.get(batchId),
            direction.includesUpstream()
                ? buildUpstream(batchId, upstreamByOutput, batchViews, new LinkedHashSet<>(Set.of(batchId)))
                : List.of(),
            direction.includesDownstream()
                ? buildDownstream(batchId, downstreamBySource, batchViews, new LinkedHashSet<>(Set.of(batchId)))
                : List.of()
        );
    }

    private List<ProductionGenealogyEdgeRecord> sorted(List<ProductionGenealogyEdgeRecord> edges) {
        return edges.stream().sorted(EDGE_ORDER).toList();
    }

    private Set<UUID> collectBatchIds(
        UUID rootBatchId,
        List<ProductionGenealogyEdgeRecord> upstream,
        List<ProductionGenealogyEdgeRecord> downstream
    ) {
        var ids = new LinkedHashSet<UUID>();
        ids.add(rootBatchId);
        upstream.forEach(edge -> {
            ids.add(edge.sourceBatchId());
            ids.add(edge.outputBatchId());
        });
        downstream.forEach(edge -> {
            ids.add(edge.sourceBatchId());
            ids.add(edge.outputBatchId());
        });
        return Set.copyOf(ids);
    }

    private Map<UUID, BatchDetails> indexBatchDetails(Set<UUID> batchIds) {
        var indexed = new HashMap<UUID, BatchDetails>();
        for (var details : batchDetailsLookup.findByIds(batchIds)) {
            indexed.put(details.id(), details);
        }
        return Map.copyOf(indexed);
    }

    private Map<UUID, InventoryItemDetails> indexItemDetails(Collection<BatchDetails> batches) {
        var itemIds = batches.stream()
            .map(BatchDetails::inventoryItemId)
            .collect(java.util.stream.Collectors.toSet());
        var indexed = new HashMap<UUID, InventoryItemDetails>();
        for (var details : inventoryItemDetailsLookup.findByIds(itemIds)) {
            indexed.put(details.id(), details);
        }
        if (indexed.size() != itemIds.size()) {
            throw new IllegalStateException("Genealogy references missing catalog item details");
        }
        return Map.copyOf(indexed);
    }

    private Map<UUID, GenealogyBatch> buildBatchViews(
        Map<UUID, BatchDetails> batches,
        Map<UUID, InventoryItemDetails> items,
        Set<UUID> producedBatchIds
    ) {
        var result = new HashMap<UUID, GenealogyBatch>();
        for (var batch : batches.values()) {
            var item = items.get(batch.inventoryItemId());
            if (item == null) {
                throw new IllegalStateException("Genealogy references a missing catalog item");
            }
            result.put(batch.id(), new GenealogyBatch(
                batch.id(),
                producedBatchIds.contains(batch.id())
                    ? GenealogyBatchOrigin.INTERNALLY_PRODUCED
                    : GenealogyBatchOrigin.EXTERNAL_OR_NON_PRODUCED,
                item.id(),
                item.name(),
                item.category(),
                item.unitOfMeasure(),
                batch.supplierId(),
                batch.lotCode(),
                batch.receivedAt(),
                batch.expiresAt()
            ));
        }
        return Map.copyOf(result);
    }

    private Map<UUID, List<ProductionGenealogyEdgeRecord>> indexByOutputBatch(
        List<ProductionGenealogyEdgeRecord> edges
    ) {
        var result = new LinkedHashMap<UUID, List<ProductionGenealogyEdgeRecord>>();
        edges.forEach(edge -> result.computeIfAbsent(edge.outputBatchId(), ignored -> new ArrayList<>()).add(edge));
        return result;
    }

    private Map<UUID, List<ProductionGenealogyEdgeRecord>> indexBySourceBatch(
        List<ProductionGenealogyEdgeRecord> edges
    ) {
        var result = new LinkedHashMap<UUID, List<ProductionGenealogyEdgeRecord>>();
        edges.forEach(edge -> result.computeIfAbsent(edge.sourceBatchId(), ignored -> new ArrayList<>()).add(edge));
        return result;
    }

    private List<GenealogyEdge> buildUpstream(
        UUID outputBatchId,
        Map<UUID, List<ProductionGenealogyEdgeRecord>> edgesByOutput,
        Map<UUID, GenealogyBatch> batches,
        Set<UUID> path
    ) {
        return edgesByOutput.getOrDefault(outputBatchId, List.of()).stream()
            .map(edge -> {
                var nextPath = new LinkedHashSet<>(path);
                var next = nextPath.add(edge.sourceBatchId())
                    ? buildUpstream(edge.sourceBatchId(), edgesByOutput, batches, nextPath)
                    : List.<GenealogyEdge>of();
                return toEdge(edge, batches, next);
            })
            .toList();
    }

    private List<GenealogyEdge> buildDownstream(
        UUID sourceBatchId,
        Map<UUID, List<ProductionGenealogyEdgeRecord>> edgesBySource,
        Map<UUID, GenealogyBatch> batches,
        Set<UUID> path
    ) {
        return edgesBySource.getOrDefault(sourceBatchId, List.of()).stream()
            .map(edge -> {
                var nextPath = new LinkedHashSet<>(path);
                var next = nextPath.add(edge.outputBatchId())
                    ? buildDownstream(edge.outputBatchId(), edgesBySource, batches, nextPath)
                    : List.<GenealogyEdge>of();
                return toEdge(edge, batches, next);
            })
            .toList();
    }

    private GenealogyEdge toEdge(
        ProductionGenealogyEdgeRecord edge,
        Map<UUID, GenealogyBatch> batches,
        List<GenealogyEdge> next
    ) {
        var source = batches.get(edge.sourceBatchId());
        var output = batches.get(edge.outputBatchId());
        if (source == null || output == null) {
            throw new IllegalStateException("Genealogy references a missing inventory batch");
        }
        return new GenealogyEdge(
            edge.executionId(),
            edge.formulaId(),
            edge.productionDate(),
            edge.completedAt(),
            edge.consumedQuantity(),
            source,
            output,
            next
        );
    }
}
