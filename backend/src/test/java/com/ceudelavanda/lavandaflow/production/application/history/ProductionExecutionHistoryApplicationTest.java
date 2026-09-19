package com.ceudelavanda.lavandaflow.production.application.history;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemDetails;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemDetailsLookup;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.BatchDetails;
import com.ceudelavanda.lavandaflow.inventory.BatchDetailsLookup;
import com.ceudelavanda.lavandaflow.production.domain.ProductionLotCodeMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionExecutionHistoryApplicationTest {

    @Mock private ProductionExecutionHistoryQuery historyQuery;
    @Mock private InventoryItemDetailsLookup itemDetailsLookup;
    @Mock private BatchDetailsLookup batchDetailsLookup;

    @Test
    void shouldBulkEnrichListWithoutLoadingBatchMetadata() {
        var outputItemId = UUID.randomUUID();
        var query = new GetProductionExecutionHistoryQuery(null, null, 0, 20);
        when(historyQuery.find(query)).thenReturn(new ProductionExecutionHistoryQuery.Page(
            List.of(summary(outputItemId), summary(outputItemId)), 0, 20, 2, 1
        ));
        when(itemDetailsLookup.findByIds(List.of(outputItemId)))
            .thenReturn(List.of(item(outputItemId, "Sabonete")));

        var result = new GetProductionExecutionHistory(historyQuery, itemDetailsLookup).execute(query);

        assertThat(result.content()).hasSize(2).allSatisfy(entry -> {
            assertThat(entry.outputItemName()).isEqualTo("Sabonete");
            assertThat(entry.outputQuantity()).isEqualByComparingTo("10.123456");
        });
        verify(itemDetailsLookup).findByIds(List.of(outputItemId));
        verifyNoInteractions(batchDetailsLookup);
    }

    @Test
    void shouldBulkEnrichDetailAndPreserveConsumptionOrder() {
        var outputItemId = UUID.randomUUID();
        var firstItemId = UUID.randomUUID();
        var secondItemId = UUID.randomUUID();
        var firstBatchId = UUID.randomUUID();
        var secondBatchId = UUID.randomUUID();
        var executionId = UUID.randomUUID();
        var detail = detail(
            executionId,
            outputItemId,
            List.of(
                consumption(firstBatchId, firstItemId, "1.100001"),
                consumption(secondBatchId, secondItemId, "2.200002")
            )
        );
        when(historyQuery.findById(executionId)).thenReturn(Optional.of(detail));
        when(itemDetailsLookup.findByIds(argThat(ids -> ids != null && Set.copyOf(ids).equals(
            Set.of(outputItemId, firstItemId, secondItemId)
        )))).thenReturn(List.of(
            item(outputItemId, "Saída"), item(firstItemId, "Fonte A"), item(secondItemId, "Fonte B")
        ));
        when(batchDetailsLookup.findByIds(List.of(firstBatchId, secondBatchId))).thenReturn(List.of(
            batch(firstBatchId, firstItemId, "A-1"), batch(secondBatchId, secondItemId, "B-1")
        ));

        var result = new GetProductionExecutionDetails(
            historyQuery, itemDetailsLookup, batchDetailsLookup
        ).execute(executionId);

        assertThat(result.consumptions())
            .extracting(ProductionExecutionDetailsResult.Consumption::sourceLotCode)
            .containsExactly("A-1", "B-1");
        assertThat(result.consumptions())
            .extracting(ProductionExecutionDetailsResult.Consumption::quantity)
            .containsExactly(new BigDecimal("1.100001"), new BigDecimal("2.200002"));
        verify(itemDetailsLookup).findByIds(argThat(ids -> ids != null && Set.copyOf(ids).equals(
            Set.of(outputItemId, firstItemId, secondItemId)
        )));
        verify(batchDetailsLookup).findByIds(List.of(firstBatchId, secondBatchId));
    }

    @Test
    void shouldExposeNotFoundAndFailExplicitlyForBrokenPersistedReferences() {
        var missingExecutionId = UUID.randomUUID();
        when(historyQuery.findById(missingExecutionId)).thenReturn(Optional.empty());
        var service = new GetProductionExecutionDetails(
            historyQuery, itemDetailsLookup, batchDetailsLookup
        );

        assertThatThrownBy(() -> service.execute(missingExecutionId))
            .isInstanceOf(ProductionExecutionNotFoundException.class);

        var outputItemId = UUID.randomUUID();
        var sourceItemId = UUID.randomUUID();
        var sourceBatchId = UUID.randomUUID();
        var executionId = UUID.randomUUID();
        when(historyQuery.findById(executionId)).thenReturn(Optional.of(detail(
            executionId,
            outputItemId,
            List.of(consumption(sourceBatchId, sourceItemId, "1"))
        )));
        when(itemDetailsLookup.findByIds(argThat(ids -> ids != null && Set.copyOf(ids).equals(
            Set.of(outputItemId, sourceItemId)
        )))).thenReturn(List.of(item(outputItemId, "Saída"), item(sourceItemId, "Fonte")));
        when(batchDetailsLookup.findByIds(List.of(sourceBatchId))).thenReturn(List.of());

        assertThatThrownBy(() -> service.execute(executionId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("missing source batch");
    }

    @Test
    void shouldFailExplicitlyForMissingItemAndBatchItemMismatch() {
        var outputItemId = UUID.randomUUID();
        var sourceItemId = UUID.randomUUID();
        var sourceBatchId = UUID.randomUUID();
        var executionId = UUID.randomUUID();
        var detail = detail(
            executionId,
            outputItemId,
            List.of(consumption(sourceBatchId, sourceItemId, "1"))
        );
        when(historyQuery.findById(executionId)).thenReturn(Optional.of(detail));
        when(itemDetailsLookup.findByIds(argThat(ids -> ids != null && Set.copyOf(ids).equals(
            Set.of(outputItemId, sourceItemId)
        )))).thenReturn(List.of(item(outputItemId, "Saída")));
        when(batchDetailsLookup.findByIds(List.of(sourceBatchId)))
            .thenReturn(List.of(batch(sourceBatchId, sourceItemId, "LOT")));
        var service = new GetProductionExecutionDetails(
            historyQuery, itemDetailsLookup, batchDetailsLookup
        );

        assertThatThrownBy(() -> service.execute(executionId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("missing catalog item");

        when(itemDetailsLookup.findByIds(argThat(ids -> ids != null && Set.copyOf(ids).equals(
            Set.of(outputItemId, sourceItemId)
        )))).thenReturn(List.of(item(outputItemId, "Saída"), item(sourceItemId, "Fonte")));
        when(batchDetailsLookup.findByIds(List.of(sourceBatchId)))
            .thenReturn(List.of(batch(sourceBatchId, UUID.randomUUID(), "LOT")));

        assertThatThrownBy(() -> service.execute(executionId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("identity mismatch");
    }

    private static ProductionExecutionHistoryQuery.Summary summary(UUID outputItemId) {
        return new ProductionExecutionHistoryQuery.Summary(
            UUID.randomUUID(), UUID.randomUUID(), outputItemId, UUID.randomUUID(),
            new BigDecimal("10.123456"), "LOT", ProductionLotCodeMode.MANUAL,
            LocalDate.of(2026, 9, 18), Instant.parse("2026-09-18T12:00:00Z")
        );
    }

    private static ProductionExecutionHistoryQuery.Detail detail(
        UUID executionId,
        UUID outputItemId,
        List<ProductionExecutionHistoryQuery.Consumption> consumptions
    ) {
        return new ProductionExecutionHistoryQuery.Detail(
            executionId, UUID.randomUUID(), outputItemId, UUID.randomUUID(),
            new BigDecimal("10.123456"), "OUT", ProductionLotCodeMode.GENERATED,
            LocalDate.of(2026, 9, 18), LocalDate.of(2026, 9, 18), null,
            Instant.parse("2026-09-18T12:00:00Z"), consumptions
        );
    }

    private static ProductionExecutionHistoryQuery.Consumption consumption(
        UUID batchId,
        UUID itemId,
        String quantity
    ) {
        return new ProductionExecutionHistoryQuery.Consumption(
            batchId, itemId, UUID.randomUUID(), new BigDecimal(quantity)
        );
    }

    private static InventoryItemDetails item(UUID id, String name) {
        return new InventoryItemDetails(id, name, "OTHER", UnitOfMeasure.MILLILITER, true);
    }

    private static BatchDetails batch(UUID id, UUID itemId, String lotCode) {
        return new BatchDetails(id, itemId, null, lotCode, LocalDate.of(2026, 9, 18), null);
    }
}
