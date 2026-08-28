package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InventoryItemManagementIntegrationTest {

    @Autowired
    private RegisterInventoryItem registerInventoryItem;

    @Autowired
    private GetInventoryItem getInventoryItem;

    @Autowired
    private SearchInventoryItems searchInventoryItems;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Test
    void shouldRegisterAndRetrieveInventoryItem() {
        var registered = registerInventoryItem.execute(new RegisterInventoryItemCommand(
            "Lavender Essence",
            "Floral raw material",
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        ));

        var retrieved = getInventoryItem.execute(registered.id());

        assertThat(retrieved).isEqualTo(registered);
        assertThat(retrieved.active()).isTrue();
    }

    @Test
    void shouldFilterAndOrderItemsDeterministicallyWithoutInventingNameUniqueness() {
        var lowerId = new UUID(0, 1);
        var higherId = new UUID(0, 2);
        var inactiveId = new UUID(0, 3);
        inventoryItemRepository.save(new InventoryItem(
            higherId, "Lavender Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, true
        ));
        inventoryItemRepository.save(new InventoryItem(
            lowerId, "lavender essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, true
        ));
        inventoryItemRepository.save(new InventoryItem(
            inactiveId, "Lavender Bottle", null, Category.BOTTLE, UnitOfMeasure.UNIT, false
        ));

        var filtered = searchInventoryItems.execute(new InventoryItemSearchQuery(
            "LAVENDER", Category.ESSENCE, true, 0, 20
        ));
        var inactive = searchInventoryItems.execute(new InventoryItemSearchQuery(
            "bottle", Category.BOTTLE, false, 0, 20
        ));

        assertThat(filtered.content()).extracting(InventoryItemResult::id)
            .containsExactly(lowerId, higherId);
        assertThat(filtered.totalElements()).isEqualTo(2);
        assertThat(inactive.content()).extracting(InventoryItemResult::id)
            .containsExactly(inactiveId);
    }
}
