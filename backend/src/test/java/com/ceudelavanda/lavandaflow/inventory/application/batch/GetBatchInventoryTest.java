package com.ceudelavanda.lavandaflow.inventory.application.batch;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemLookup;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemSnapshot;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InventoryItemNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetBatchInventoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 28);
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-08-28T15:00:00Z"),
        ZoneId.of("America/Sao_Paulo")
    );

    @Mock private InventoryItemLookup inventoryItemLookup;
    @Mock private BatchInventoryQuery batchInventoryQuery;

    private GetBatchInventory getBatchInventory;

    @BeforeEach
    void setUp() {
        getBatchInventory = new GetBatchInventory(inventoryItemLookup, batchInventoryQuery, CLOCK);
    }

    @Test
    void shouldDeriveOperationalStatusesAndPreserveProjectionData() {
        var itemId = UUID.randomUUID();
        var supplierId = UUID.randomUUID();
        var zeroExpired = record(itemId, supplierId, "ZERO", "10.000000", "0.000000", TODAY.minusDays(2));
        var expiresToday = record(itemId, supplierId, "TODAY", "12.500000", "5.250000", TODAY);
        var future = record(itemId, null, "FUTURE", "9.123456", "9.123456", TODAY.plusDays(1));
        var noExpiry = record(itemId, null, null, "1.000000", "1.000000", null);
        when(inventoryItemLookup.findById(itemId)).thenReturn(Optional.of(item(itemId)));
        when(batchInventoryQuery.findByInventoryItemId(itemId)).thenReturn(List.of(
            zeroExpired, expiresToday, future, noExpiry
        ));

        var result = getBatchInventory.execute(itemId);

        assertThat(result.inventoryItemId()).isEqualTo(itemId);
        assertThat(result.asOfDate()).isEqualTo(TODAY);
        assertThat(result.batches()).extracting(BatchInventoryEntryResult::status).containsExactly(
            BatchOperationalStatus.ZERO_BALANCE,
            BatchOperationalStatus.EXPIRED,
            BatchOperationalStatus.AVAILABLE,
            BatchOperationalStatus.AVAILABLE
        );
        assertThat(result.batches().get(1).supplierId()).isEqualTo(supplierId);
        assertThat(result.batches().get(1).currentQuantity()).isEqualByComparingTo("5.250000");
        assertThat(result.batches().get(2).initialQuantity()).isEqualByComparingTo("9.123456");
        assertThat(result.batches().get(3).lotCode()).isNull();
        assertThat(result.batches().get(3).expiresAt()).isNull();
    }

    @Test
    void shouldReturnEmptyBatchListForExistingItemWithoutBatches() {
        var itemId = UUID.randomUUID();
        when(inventoryItemLookup.findById(itemId)).thenReturn(Optional.of(item(itemId)));
        when(batchInventoryQuery.findByInventoryItemId(itemId)).thenReturn(List.of());

        var result = getBatchInventory.execute(itemId);

        assertThat(result.batches()).isEmpty();
        assertThat(result.asOfDate()).isEqualTo(TODAY);
    }

    @Test
    void shouldRejectUnknownInventoryItemBeforeQueryingBatches() {
        var itemId = UUID.randomUUID();
        when(inventoryItemLookup.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getBatchInventory.execute(itemId))
            .isInstanceOf(InventoryItemNotFoundException.class);

        verify(batchInventoryQuery, never()).findByInventoryItemId(itemId);
    }

    private static InventoryItemSnapshot item(UUID itemId) {
        return new InventoryItemSnapshot(itemId, "Issue95 Item", UnitOfMeasure.MILLILITER, true);
    }

    private static BatchInventoryRecord record(
        UUID itemId,
        UUID supplierId,
        String lotCode,
        String initialQuantity,
        String currentQuantity,
        LocalDate expiresAt
    ) {
        return new BatchInventoryRecord(
            UUID.randomUUID(),
            itemId,
            supplierId,
            lotCode,
            new BigDecimal(initialQuantity),
            new BigDecimal(currentQuantity),
            TODAY.minusMonths(1),
            expiresAt
        );
    }
}
