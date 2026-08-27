package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.domain.MinimumStockLevel;
import com.ceudelavanda.lavandaflow.inventory.domain.MinimumStockLevelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JpaMinimumStockLevelRepositoryTest {

    @Autowired private MinimumStockLevelRepository minimumStockLevelRepository;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistUpdateAndDeleteMinimumStockLevelWithStableCreationTimestamp() throws Exception {
        var item = inventoryItemRepository.save(InventoryItem.create("Minimum level item", null, Category.ESSENCE, UnitOfMeasure.MILLILITER));
        minimumStockLevelRepository.save(new MinimumStockLevel(item.getId(), new BigDecimal("10")));
        var createdAt = timestamp("created_at", item.getId());
        var firstUpdatedAt = timestamp("updated_at", item.getId());

        Thread.sleep(2);
        minimumStockLevelRepository.save(new MinimumStockLevel(item.getId(), new BigDecimal("20.5")));

        assertThat(minimumStockLevelRepository.findByInventoryItemId(item.getId()))
            .isPresent().get().satisfies(level -> assertThat(level.getMinimumQuantity()).isEqualByComparingTo("20.500000"));
        assertThat(timestamp("created_at", item.getId())).isEqualTo(createdAt);
        assertThat(timestamp("updated_at", item.getId())).isAfter(firstUpdatedAt);
        minimumStockLevelRepository.deleteByInventoryItemId(item.getId());
        assertThat(minimumStockLevelRepository.findByInventoryItemId(item.getId())).isEmpty();
    }

    @Test
    void shouldEnforceForeignKeyAndPositiveQuantityAtDatabaseLevel() {
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO inventory_minimum_stock_level (inventory_item_id, minimum_quantity) VALUES (?, ?)",
            java.util.UUID.randomUUID(), BigDecimal.ONE
        )).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        var item = inventoryItemRepository.save(InventoryItem.create("Constraint item", null, Category.BOTTLE, UnitOfMeasure.UNIT));
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO inventory_minimum_stock_level (inventory_item_id, minimum_quantity) VALUES (?, ?)",
            item.getId(), BigDecimal.ZERO
        )).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private Instant timestamp(String column, java.util.UUID itemId) {
        return jdbcTemplate.queryForObject(
            "SELECT " + column + " FROM inventory_minimum_stock_level WHERE inventory_item_id = ?",
            Instant.class,
            itemId
        );
    }
}
