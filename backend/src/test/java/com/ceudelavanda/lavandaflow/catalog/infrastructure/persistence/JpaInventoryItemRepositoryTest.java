package com.ceudelavanda.lavandaflow.catalog.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.catalog.domain.UnitOfMeasure;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class JpaInventoryItemRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
        new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private InventoryItemRepository repository;

    @Test
    void shouldSaveAndFindInventoryItem() {
        var item = InventoryItem.create(
            "Good Girl Essence",
            "Fragrance essence",
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        );

        repository.save(item);

        var persistedItem = repository.findById(item.getId());

        assertThat(persistedItem)
            .isPresent()
            .get()
            .satisfies(found -> {
                assertThat(found.getId()).isEqualTo(item.getId());
                assertThat(found.getName()).isEqualTo("Good Girl Essence");
                assertThat(found.getDescription()).isEqualTo("Fragrance essence");
                assertThat(found.getCategory()).isEqualTo(Category.ESSENCE);
                assertThat(found.getUnitOfMeasure())
                    .isEqualTo(UnitOfMeasure.MILLILITER);
                assertThat(found.isActive()).isTrue();
            });
    }

    @Test
    void shouldPersistInactiveState() {
        var item = InventoryItem.create(
            "Bottle 200ml",
            null,
            Category.BOTTLE,
            UnitOfMeasure.UNIT
        );

        item.deactivate();

        repository.save(item);

        var persistedItem = repository.findById(item.getId());

        assertThat(persistedItem)
            .isPresent()
            .get()
            .satisfies(found -> {
                assertThat(found.getDescription()).isNull();
                assertThat(found.isActive()).isFalse();
            });
    }
}
