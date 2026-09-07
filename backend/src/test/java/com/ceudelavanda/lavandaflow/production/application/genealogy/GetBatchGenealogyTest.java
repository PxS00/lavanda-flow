package com.ceudelavanda.lavandaflow.production.application.genealogy;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemDetails;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemDetailsLookup;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.BatchDetails;
import com.ceudelavanda.lavandaflow.inventory.BatchDetailsLookup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetBatchGenealogyTest {

    @Mock private ProductionGenealogyQueryRepository genealogyRepository;
    @Mock private BatchDetailsLookup batchDetailsLookup;
    @Mock private InventoryItemDetailsLookup inventoryItemDetailsLookup;

    @Test
    void shouldBulkEnrichAllBatchesForBothDirections() {
        var rootBatchId = UUID.randomUUID();
        var sourceBatchId = UUID.randomUUID();
        var outputBatchId = UUID.randomUUID();
        var rootItemId = UUID.randomUUID();
        var sourceItemId = UUID.randomUUID();
        var outputItemId = UUID.randomUUID();
        var upstream = edge(sourceBatchId, rootBatchId);
        var downstream = edge(rootBatchId, outputBatchId);
        var batchIds = Set.of(rootBatchId, sourceBatchId, outputBatchId);
        var itemIds = Set.of(rootItemId, sourceItemId, outputItemId);

        when(genealogyRepository.findUpstreamEdges(rootBatchId)).thenReturn(List.of(upstream));
        when(genealogyRepository.findDownstreamEdges(rootBatchId)).thenReturn(List.of(downstream));
        when(batchDetailsLookup.findByIds(argThat(ids -> Set.copyOf(ids).equals(batchIds))))
            .thenReturn(List.of(
                batch(rootBatchId, rootItemId),
                batch(sourceBatchId, sourceItemId),
                batch(outputBatchId, outputItemId)
            ));
        when(inventoryItemDetailsLookup.findByIds(argThat(ids -> Set.copyOf(ids).equals(itemIds))))
            .thenReturn(List.of(
                item(rootItemId),
                item(sourceItemId),
                item(outputItemId)
            ));
        when(genealogyRepository.findProducedBatchIds(argThat(ids -> Set.copyOf(ids).equals(batchIds))))
            .thenReturn(Set.of(rootBatchId, outputBatchId));

        var result = new GetBatchGenealogy(
            genealogyRepository,
            batchDetailsLookup,
            inventoryItemDetailsLookup
        ).execute(rootBatchId, GenealogyDirection.BOTH);

        assertThat(result.upstream()).hasSize(1);
        assertThat(result.downstream()).hasSize(1);
        verify(batchDetailsLookup).findByIds(argThat(ids -> Set.copyOf(ids).equals(batchIds)));
        verify(inventoryItemDetailsLookup).findByIds(argThat(ids -> Set.copyOf(ids).equals(itemIds)));
    }

    private static ProductionGenealogyEdgeRecord edge(UUID sourceBatchId, UUID outputBatchId) {
        return new ProductionGenealogyEdgeRecord(
            UUID.randomUUID(),
            UUID.randomUUID(),
            sourceBatchId,
            outputBatchId,
            BigDecimal.ONE,
            LocalDate.of(2026, 9, 3),
            Instant.parse("2026-09-03T12:00:00Z")
        );
    }

    private static BatchDetails batch(UUID batchId, UUID itemId) {
        return new BatchDetails(batchId, itemId, null, "LOT", LocalDate.of(2026, 9, 3), null);
    }

    private static InventoryItemDetails item(UUID itemId) {
        return new InventoryItemDetails(itemId, "Item", "OTHER", UnitOfMeasure.MILLILITER, true);
    }
}
