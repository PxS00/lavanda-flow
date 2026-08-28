package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemLookup;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemSnapshot;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.application.stock.GetCurrentStock;
import com.ceudelavanda.lavandaflow.inventory.application.stock.GetCurrentStockQuery;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InventoryItemNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCurrentStockTest {

    @Mock
    private InventoryItemLookup inventoryItemLookup;

    @Mock
    private BatchRepository batchRepository;

    private GetCurrentStock getCurrentStock;

    @BeforeEach
    void setUp() {
        getCurrentStock = new GetCurrentStock(inventoryItemLookup, batchRepository);
    }

    @Test
    void shouldReturnCurrentStockForExistingItem() {
        var itemId = UUID.randomUUID();
        var supplierId = UUID.randomUUID();
        var batch = batch(itemId, UUID.randomUUID(), supplierId, "ESS-LAV-042", "30.500", "2026-06-10", "2026-09-15");
        item(itemId, true);
        when(batchRepository.findByInventoryItemId(itemId)).thenReturn(List.of(batch));

        var result = getCurrentStock.execute(query(itemId, false));

        verify(inventoryItemLookup).findById(itemId);
        verify(batchRepository).findByInventoryItemId(itemId);
        assertThat(result.inventoryItemId()).isEqualTo(itemId);
        assertThat(result.active()).isTrue();
        assertThat(result.totalCurrentQuantity()).isEqualByComparingTo("30.500");
        assertThat(result.batches()).singleElement().satisfies(resultBatch -> {
            assertThat(resultBatch.batchId()).isEqualTo(batch.getId());
            assertThat(resultBatch.supplierId()).isEqualTo(supplierId);
            assertThat(resultBatch.lotCode()).isEqualTo("ESS-LAV-042");
            assertThat(resultBatch.currentQuantity()).isEqualByComparingTo("30.500");
            assertThat(resultBatch.receivedAt()).isEqualTo(LocalDate.of(2026, 6, 10));
            assertThat(resultBatch.expiresAt()).isEqualTo(LocalDate.of(2026, 9, 15));
        });
    }

    @Test
    void shouldCalculateTotalFromAllBatches() {
        var itemId = UUID.randomUUID();
        item(itemId, true);
        when(batchRepository.findByInventoryItemId(itemId)).thenReturn(List.of(
            batch(itemId, UUID.randomUUID(), null, "A", "10.5", "2026-06-10", "2026-09-15"),
            batch(itemId, UUID.randomUUID(), null, "B", "20", "2026-06-11", "2026-09-16"),
            batch(itemId, UUID.randomUUID(), null, "C", "4.25", "2026-06-12", "2026-09-17")
        ));

        var result = getCurrentStock.execute(query(itemId, false));

        assertThat(result.totalCurrentQuantity()).isEqualByComparingTo("34.75");
    }

    @Test
    void shouldExcludeZeroBalanceBatchesByDefault() {
        var itemId = UUID.randomUUID();
        var positiveBatch = batch(itemId, UUID.randomUUID(), null, "POSITIVE", "10", "2026-06-10", "2026-09-15");
        var zeroBatch = batch(itemId, UUID.randomUUID(), null, "ZERO", "0", "2026-06-11", "2026-09-16");
        item(itemId, true);
        when(batchRepository.findByInventoryItemId(itemId)).thenReturn(List.of(positiveBatch, zeroBatch));

        var result = getCurrentStock.execute(query(itemId, false));

        assertThat(result.totalCurrentQuantity()).isEqualByComparingTo("10");
        assertThat(result.batches()).extracting(batch -> batch.batchId())
            .containsExactly(positiveBatch.getId());
    }

    @Test
    void shouldIncludeZeroBalanceBatchesWhenRequested() {
        var itemId = UUID.randomUUID();
        var positiveBatch = batch(itemId, UUID.randomUUID(), null, "POSITIVE", "10", "2026-06-10", "2026-09-15");
        var zeroBatch = batch(itemId, UUID.randomUUID(), null, "ZERO", "0", "2026-06-11", "2026-09-16");
        item(itemId, true);
        when(batchRepository.findByInventoryItemId(itemId)).thenReturn(List.of(positiveBatch, zeroBatch));

        var result = getCurrentStock.execute(query(itemId, true));

        assertThat(result.totalCurrentQuantity()).isEqualByComparingTo("10");
        assertThat(result.batches()).extracting(batch -> batch.batchId())
            .containsExactly(positiveBatch.getId(), zeroBatch.getId());
    }

    @Test
    void shouldKeepExpiredBatchesInCurrentStock() {
        var itemId = UUID.randomUUID();
        var expiredBatch = batch(itemId, UUID.randomUUID(), null, "EXPIRED", "12.5", "2025-01-10", "2025-02-01");
        item(itemId, true);
        when(batchRepository.findByInventoryItemId(itemId)).thenReturn(List.of(expiredBatch));

        var result = getCurrentStock.execute(query(itemId, false));

        assertThat(result.totalCurrentQuantity()).isEqualByComparingTo("12.5");
        assertThat(result.batches()).singleElement().satisfies(batch -> {
            assertThat(batch.batchId()).isEqualTo(expiredBatch.getId());
            assertThat(batch.expiresAt()).isEqualTo(LocalDate.of(2025, 2, 1));
        });
    }

    @Test
    void shouldReturnInactiveItemStock() {
        var itemId = UUID.randomUUID();
        var stockBatch = batch(itemId, UUID.randomUUID(), null, "INACTIVE", "5", "2026-06-10", null);
        item(itemId, false);
        when(batchRepository.findByInventoryItemId(itemId)).thenReturn(List.of(stockBatch));

        var result = getCurrentStock.execute(query(itemId, false));

        assertThat(result.active()).isFalse();
        assertThat(result.totalCurrentQuantity()).isEqualByComparingTo("5");
        assertThat(result.batches()).singleElement();
    }

    @Test
    void shouldReturnZeroForItemWithoutBatches() {
        var itemId = UUID.randomUUID();
        item(itemId, true);
        when(batchRepository.findByInventoryItemId(itemId)).thenReturn(List.of());

        var result = getCurrentStock.execute(query(itemId, false));

        assertThat(result.totalCurrentQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.batches()).isEmpty();
    }

    @Test
    void shouldThrowWhenInventoryItemDoesNotExist() {
        var itemId = UUID.randomUUID();
        when(inventoryItemLookup.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getCurrentStock.execute(query(itemId, false)))
            .isInstanceOf(InventoryItemNotFoundException.class);

        verify(batchRepository, never()).findByInventoryItemId(any());
    }

    @Test
    void shouldOrderBatchesDeterministically() {
        var itemId = UUID.randomUUID();
        var earlierReceived = batch(itemId, uuid(1), null, "EARLIER", "1", "2026-06-10", "2026-09-01");
        var lowerId = batch(itemId, uuid(2), null, "LOWER-ID", "1", "2026-06-11", "2026-09-01");
        var higherId = batch(itemId, uuid(3), null, "HIGHER-ID", "1", "2026-06-11", "2026-09-01");
        var laterExpiry = batch(itemId, uuid(4), null, "LATER-EXPIRY", "1", "2026-06-09", "2026-09-02");
        var noExpiry = batch(itemId, uuid(5), null, "NO-EXPIRY", "1", "2026-06-01", null);
        item(itemId, true);
        when(batchRepository.findByInventoryItemId(itemId)).thenReturn(List.of(
            noExpiry, higherId, laterExpiry, lowerId, earlierReceived
        ));

        var result = getCurrentStock.execute(query(itemId, false));

        assertThat(result.batches()).extracting(batch -> batch.batchId()).containsExactly(
            earlierReceived.getId(), lowerId.getId(), higherId.getId(), laterExpiry.getId(), noExpiry.getId()
        );
    }

    @Test
    void shouldPreserveNullableBatchMetadata() {
        var itemId = UUID.randomUUID();
        var stockBatch = batch(itemId, UUID.randomUUID(), null, null, "7.25", "2026-06-10", null);
        item(itemId, true);
        when(batchRepository.findByInventoryItemId(itemId)).thenReturn(List.of(stockBatch));

        var result = getCurrentStock.execute(query(itemId, false));

        assertThat(result.batches()).singleElement().satisfies(batch -> {
            assertThat(batch.supplierId()).isNull();
            assertThat(batch.lotCode()).isNull();
            assertThat(batch.expiresAt()).isNull();
        });
    }

    private void item(UUID itemId, boolean active) {
        when(inventoryItemLookup.findById(itemId))
            .thenReturn(Optional.of(new InventoryItemSnapshot(itemId, "Test item", UnitOfMeasure.UNIT, active)));
    }

    private static GetCurrentStockQuery query(UUID itemId, boolean includeZeroBalance) {
        return new GetCurrentStockQuery(itemId, includeZeroBalance);
    }

    private static Batch batch(
        UUID itemId,
        UUID batchId,
        UUID supplierId,
        String lotCode,
        String currentQuantity,
        String receivedAt,
        String expiresAt
    ) {
        return new Batch(
            batchId,
            itemId,
            supplierId,
            lotCode,
            BigDecimal.ONE,
            new BigDecimal(currentQuantity),
            LocalDate.parse(receivedAt),
            expiresAt == null ? null : LocalDate.parse(expiresAt)
        );
    }

    private static UUID uuid(long value) {
        return new UUID(0, value);
    }
}
