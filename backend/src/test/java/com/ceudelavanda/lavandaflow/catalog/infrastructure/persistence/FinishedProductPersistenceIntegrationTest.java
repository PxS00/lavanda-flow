package com.ceudelavanda.lavandaflow.catalog.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.FinishedProductRegistration;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemRegistration;
import com.ceudelavanda.lavandaflow.catalog.ProductGender;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FinishedProductPersistenceIntegrationTest {
    @Autowired InventoryItemRegistration registration;
    @Autowired InventoryItemRepository repository;
    @Autowired JdbcTemplate jdbc;

    @ParameterizedTest
    @EnumSource(ProductGender.class)
    void shouldRegisterSharedReferencesThroughPublicContract(ProductGender gender) {
        for (var unit : new UnitOfMeasure[]{UnitOfMeasure.MILLILITER, UnitOfMeasure.UNIT}) {
            var snapshot = registration.registerFinishedProduct(new FinishedProductRegistration(
                "Issue229 Perfume", "Presentation", unit, "229", "PRF", gender));
            var item = repository.findById(snapshot.id()).orElseThrow();
            assertThat(item.getGender()).isEqualTo(gender);
            assertThat(item.getEssenceReference()).isEqualTo("229");
            assertThat(item.getUnitOfMeasure()).isEqualTo(unit);
            assertThat(jdbc.queryForObject("select product_gender from inventory_item where id = ?",
                String.class, item.getId())).isEqualTo(gender.code());
        }
    }

    @Test
    void shouldProtectCanonicalUniquenessAndAssignedReferences() {
        var essence = insert("ESSENCE", "728", "C");
        var product = insert("FINISHED_PRODUCT", "728", "C");
        insert("FINISHED_PRODUCT", "728", "C");
        assertThatThrownBy(() -> insert("ESSENCE", "728", "C")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update inventory_item set category = 'FINISHED_PRODUCT' where id = ?", essence))
            .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.update("update inventory_item set essence_reference = '728' where id = ?", product)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("update inventory_item set essence_reference = '729' where id = ?", product))
            .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update inventory_item set essence_reference = null where id = ?", product))
            .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from inventory_item where id = ?", product))
            .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insert("FINISHED_PRODUCT", "000", null)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insert("BOTTLE", null, "C")).isInstanceOf(DataAccessException.class);
        insert("BOTTLE", null, null);
        insert("FINISHED_PRODUCT", null, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "X", "MC", "m"})
    void shouldRejectInvalidStoredGender(String gender) {
        assertThatThrownBy(() -> insert("FINISHED_PRODUCT", null, gender)).isInstanceOf(DataAccessException.class);
    }

    private UUID insert(String category, String reference, String gender) {
        var id = UUID.randomUUID();
        jdbc.update("""
            insert into inventory_item (id, name, category, default_unit, active, essence_reference, product_gender)
            values (?, 'Issue229 Constraint', ?, 'UNIT', true, ?, ?)
            """, id, category, reference, gender);
        return id;
    }
}
