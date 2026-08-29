package com.ceudelavanda.lavandaflow.inventory.application.batch;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemLookup;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.suppliers.domain.Supplier;
import com.ceudelavanda.lavandaflow.suppliers.domain.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class BatchInventoryQueryIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 28);
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-08-28T15:00:00Z"),
        ZoneId.of("America/Sao_Paulo")
    );

    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private BatchRepository batchRepository;
    @Autowired private InventoryItemLookup inventoryItemLookup;
    @Autowired private BatchInventoryQuery batchInventoryQuery;

    @Test
    void shouldReadPersistedBatchesWithExpirationAwareOrderingAndDerivedStatus() {
        var item = inventoryItemRepository.save(InventoryItem.create(
            "Issue95 Batch Query Essence",
            null,
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        ));
        var supplier = supplierRepository.save(Supplier.create(
            "Issue95 Supplier",
            "SUP-95",
            null,
            null
        ));

        var expired = saveBatch(
            uuid(1), item.getId(), supplier.getId(), "EXPIRED",
            "100.000000", "25.000000", TODAY.minusMonths(2), TODAY.minusDays(2)
        );
        var zeroBalance = saveBatch(
            uuid(2), item.getId(), supplier.getId(), "ZERO",
            "50.000000", "0.000000", TODAY.minusMonths(1), TODAY.minusDays(1)
        );
        var expiresToday = saveBatch(
            uuid(3), item.getId(), null, "TODAY",
            "10.000000", "5.000000", TODAY.minusDays(10), TODAY
        );
        var lowerId = saveBatch(
            uuid(4), item.getId(), null, "FUTURE-A",
            "1234567890123.123456", "1234567890123.123456", TODAY.minusDays(5), TODAY.plusDays(10)
        );
        var higherId = saveBatch(
            uuid(5), item.getId(), null, "FUTURE-B",
            "2.000000", "2.000000", TODAY.minusDays(5), TODAY.plusDays(10)
        );
        var noExpiry = saveBatch(
            uuid(6), item.getId(), null, null,
            "1.000000", "1.000000", TODAY.minusMonths(3), null
        );

        var useCase = new GetBatchInventory(inventoryItemLookup, batchInventoryQuery, CLOCK);
        var result = useCase.execute(item.getId());

        assertThat(result.asOfDate()).isEqualTo(TODAY);
        assertThat(result.batches()).extracting(BatchInventoryEntryResult::batchId).containsExactly(
            expired.getId(),
            zeroBalance.getId(),
            expiresToday.getId(),
            lowerId.getId(),
            higherId.getId(),
            noExpiry.getId()
        );
        assertThat(result.batches()).extracting(BatchInventoryEntryResult::status).containsExactly(
            BatchOperationalStatus.EXPIRED,
            BatchOperationalStatus.ZERO_BALANCE,
            BatchOperationalStatus.EXPIRED,
            BatchOperationalStatus.AVAILABLE,
            BatchOperationalStatus.AVAILABLE,
            BatchOperationalStatus.AVAILABLE
        );
        assertThat(result.batches().getFirst().supplierId()).isEqualTo(supplier.getId());
        assertThat(result.batches().get(3).initialQuantity()).isEqualByComparingTo("1234567890123.123456");
        assertThat(result.batches().get(3).currentQuantity()).isEqualByComparingTo("1234567890123.123456");
        assertThat(result.batches().getLast().lotCode()).isNull();
        assertThat(result.batches().getLast().expiresAt()).isNull();
    }

    @Test
    void shouldReturnEmptyResultForExistingItemWithoutBatches() {
        var item = inventoryItemRepository.save(InventoryItem.create(
            "Issue95 Empty Batch Item",
            null,
            Category.OTHER,
            UnitOfMeasure.UNIT
        ));
        var useCase = new GetBatchInventory(inventoryItemLookup, batchInventoryQuery, CLOCK);

        var result = useCase.execute(item.getId());

        assertThat(result.inventoryItemId()).isEqualTo(item.getId());
        assertThat(result.asOfDate()).isEqualTo(TODAY);
        assertThat(result.batches()).isEmpty();
    }

    private Batch saveBatch(
        UUID batchId,
        UUID inventoryItemId,
        UUID supplierId,
        String lotCode,
        String initialQuantity,
        String currentQuantity,
        LocalDate receivedAt,
        LocalDate expiresAt
    ) {
        return batchRepository.save(new Batch(
            batchId,
            inventoryItemId,
            supplierId,
            lotCode,
            new BigDecimal(initialQuantity),
            new BigDecimal(currentQuantity),
            receivedAt,
            expiresAt
        ));
    }

    private static UUID uuid(long value) {
        return new UUID(95, value);
    }
}
