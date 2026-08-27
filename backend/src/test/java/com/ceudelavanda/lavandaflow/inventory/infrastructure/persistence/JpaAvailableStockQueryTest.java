package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.application.AvailableStockQuery;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JpaAvailableStockQueryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 26);

    @Autowired private AvailableStockQuery availableStockQuery;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private BatchRepository batchRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void shouldAggregateOnlyPositiveNonExpiredBatches() {
        var item = inventoryItemRepository.save(InventoryItem.create("Available essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER));
        batchRepository.save(Batch.create(item.getId(), null, "EXPIRED", new BigDecimal("10"), TODAY.minusDays(10), TODAY.minusDays(1)));
        batchRepository.save(Batch.create(item.getId(), null, "TODAY", new BigDecimal("20"), TODAY.minusDays(9), TODAY));
        batchRepository.save(Batch.create(item.getId(), null, "FUTURE", new BigDecimal("30"), TODAY.minusDays(8), TODAY.plusDays(1)));
        batchRepository.save(Batch.create(item.getId(), null, "NO-EXPIRY", new BigDecimal("40"), TODAY.minusDays(7), null));
        var zeroBatch = batchRepository.save(Batch.create(item.getId(), null, "ZERO", new BigDecimal("50"), TODAY.minusDays(6), TODAY.plusDays(2)));
        jdbcTemplate.update("UPDATE inventory_batch SET current_quantity = ? WHERE id = ?", BigDecimal.ZERO, zeroBatch.getId());

        var balances = availableStockQuery.findAvailableStockByInventoryItemIds(List.of(item.getId()), TODAY);

        assertThat(balances).singleElement().satisfies(balance -> {
            assertThat(balance.inventoryItemId()).isEqualTo(item.getId());
            assertThat(balance.availableQuantity()).isEqualByComparingTo("70");
        });
    }
}
