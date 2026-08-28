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
            "Issue93 Registered Essence",
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
            higherId, "Issue93 Lavender Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, true
        ));
        inventoryItemRepository.save(new InventoryItem(
            lowerId, "issue93 lavender essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, true
        ));
        inventoryItemRepository.save(new InventoryItem(
            inactiveId, "Issue93 Inactive Bottle", null, Category.BOTTLE, UnitOfMeasure.UNIT, false
        ));

        var filtered = searchInventoryItems.execute(new InventoryItemSearchQuery(
            "issue93 lavender essence", Category.ESSENCE, true, 0, 20
        ));
        var inactive = searchInventoryItems.execute(new InventoryItemSearchQuery(
            "Issue93 Inactive Bottle", Category.BOTTLE, false, 0, 20
        ));

        assertThat(filtered.content()).extracting(InventoryItemResult::id)
            .containsExactly(lowerId, higherId);
        assertThat(filtered.totalElements()).isEqualTo(2);
        assertThat(inactive.content()).extracting(InventoryItemResult::id)
            .containsExactly(inactiveId);
    }

    @Test
    void shouldTreatLikeWildcardsAsLiteralNameCharacters() {
        var percentId = new UUID(0, 4);
        var regularId = new UUID(0, 5);
        inventoryItemRepository.save(new InventoryItem(
            percentId, "Issue93 Special % Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, true
        ));
        inventoryItemRepository.save(new InventoryItem(
            regularId, "Issue93 Special Regular Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, true
        ));

        var result = searchInventoryItems.execute(new InventoryItemSearchQuery(
            "Issue93 Special %", null, null, 0, 20
        ));

        assertThat(result.content()).extracting(InventoryItemResult::id).containsExactly(percentId);
    }
}
