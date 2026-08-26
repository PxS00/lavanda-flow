package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.catalog.domain.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.application.query.GetCurrentStockQuery;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GetCurrentStockIntegrationTest {

    @Autowired
    private GetCurrentStock getCurrentStock;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldRetrievePersistedCurrentStockWithFilteringAndDeterministicOrdering() {
        var inventoryItem = inventoryItemRepository.save(InventoryItem.create(
            "Current stock integration essence",
            null,
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        ));
        var zeroBalanceBatch = batchRepository.save(Batch.create(
            inventoryItem.getId(), null, "ZERO", new BigDecimal("10.000"),
            LocalDate.of(2026, 6, 10), LocalDate.of(2026, 9, 1)
        ));
        var firstBatch = batchRepository.save(Batch.create(
            inventoryItem.getId(), null, "FIRST", new BigDecimal("20.500"),
            LocalDate.of(2026, 6, 11), LocalDate.of(2026, 9, 1)
        ));
        var secondBatch = batchRepository.save(Batch.create(
            inventoryItem.getId(), null, "SECOND", new BigDecimal("15.250"),
            LocalDate.of(2026, 6, 12), LocalDate.of(2026, 9, 2)
        ));
        var noExpiryBatch = batchRepository.save(Batch.create(
            inventoryItem.getId(), null, "NO-EXPIRY", new BigDecimal("4.000"),
            LocalDate.of(2026, 6, 1), null
        ));
        jdbcTemplate.update(
            "UPDATE inventory_batch SET current_quantity = ? WHERE id = ?",
            BigDecimal.ZERO,
            zeroBalanceBatch.getId()
        );

        var defaultResult = getCurrentStock.execute(new GetCurrentStockQuery(inventoryItem.getId(), false));
        var includingZeroResult = getCurrentStock.execute(new GetCurrentStockQuery(inventoryItem.getId(), true));

        assertThat(defaultResult.inventoryItemId()).isEqualTo(inventoryItem.getId());
        assertThat(defaultResult.active()).isTrue();
        assertThat(defaultResult.totalCurrentQuantity()).isEqualByComparingTo("39.750");
        assertThat(defaultResult.batches()).extracting(batch -> batch.batchId()).containsExactly(
            firstBatch.getId(), secondBatch.getId(), noExpiryBatch.getId()
        );
        assertThat(defaultResult.batches().getFirst().lotCode()).isEqualTo("FIRST");
        assertThat(defaultResult.batches().getFirst().currentQuantity()).isEqualByComparingTo("20.500");

        assertThat(includingZeroResult.totalCurrentQuantity()).isEqualByComparingTo("39.750");
        assertThat(includingZeroResult.batches()).extracting(batch -> batch.batchId()).containsExactly(
            zeroBalanceBatch.getId(), firstBatch.getId(), secondBatch.getId(), noExpiryBatch.getId()
        );
        assertThat(includingZeroResult.batches().getFirst().currentQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
