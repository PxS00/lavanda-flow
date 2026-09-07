package com.ceudelavanda.lavandaflow.catalog.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProductionReferencePersistenceIntegrationTest {

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistProductionReferencesAndKeepThemAcrossDisplayChanges() {
        var item = inventoryItemRepository.save(InventoryItem.create(
            "Issue146 Lavender Essence", "Initial name", Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        ));
        item.assignEssenceReference("001");
        item.assignProductionTypeCode("ESS");
        item = inventoryItemRepository.save(item);

        item.rename("Issue146 Renamed Essence");
        item.changeDescription("Changed display metadata");
        inventoryItemRepository.save(item);

        var reloaded = inventoryItemRepository.findById(item.getId()).orElseThrow();

        assertThat(reloaded.getName()).isEqualTo("Issue146 Renamed Essence");
        assertThat(reloaded.getDescription()).isEqualTo("Changed display metadata");
        assertThat(reloaded.getEssenceReference()).isEqualTo("001");
        assertThat(reloaded.getProductionTypeCode()).isEqualTo("ESS");
    }

    @Test
    void shouldEnforceProductionReferenceConstraintsInPostgres() {
        var first = inventoryItemRepository.save(InventoryItem.create(
            "Issue146 First Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, "147", null
        ));
        var duplicate = InventoryItem.create(
            "Issue146 Duplicate Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, "147", null
        );

        assertThatThrownBy(() -> inventoryItemRepository.save(duplicate))
            .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "update inventory_item set essence_reference = ? where id = ?", "148", first.getId()
        )).isInstanceOf(DataAccessException.class);
        assertThat(jdbcTemplate.update(
            "update inventory_item set essence_reference = ? where id = ?", "147", first.getId()
        )).isEqualTo(1);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "update inventory_item set essence_reference = ? where id = ?", null, first.getId()
        )).isInstanceOf(DataAccessException.class);
        assertThat(jdbcTemplate.update(
            "update inventory_item set production_type_code = ? where id = ?", "ESS", first.getId()
        )).isEqualTo(1);
        assertThat(jdbcTemplate.update(
            "update inventory_item set production_type_code = ? where id = ?", "ESS", first.getId()
        )).isEqualTo(1);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "update inventory_item set production_type_code = ? where id = ?", "BDS", first.getId()
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "update inventory_item set production_type_code = ? where id = ?", null, first.getId()
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "delete from inventory_item where id = ?", first.getId()
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "insert into inventory_item (id, name, category, default_unit, active, essence_reference) values (?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), "Issue146 Invalid Reference", "BOTTLE", "UNIT", true, "149"
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "insert into inventory_item (id, name, category, default_unit, active, essence_reference) values (?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), "Issue146 Reserved Reference", "ESSENCE", "MILLILITER", true, "000"
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "insert into inventory_item (id, name, category, default_unit, active, production_type_code) values (?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), "Issue146 Invalid Type", "OTHER", "UNIT", true, "BD"
        )).isInstanceOf(DataAccessException.class);
    }
}
