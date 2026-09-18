package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemStockLookup;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InventoryItemManagementIntegrationTest {

    @Autowired
    private RegisterInventoryItem registerInventoryItem;

    @Autowired
    private UpdateInventoryItem updateInventoryItem;

    @Autowired
    private GetInventoryItem getInventoryItem;

    @Autowired
    private SearchInventoryItems searchInventoryItems;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private InventoryItemStockLookup inventoryItemStockLookup;

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
    void shouldPersistMaintenanceOnTheSameInventoryItemIdentity() {
        var registered = registerInventoryItem.execute(new RegisterInventoryItemCommand(
            "Issue222 Original Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER
        ));

        var updated = updateInventoryItem.execute(new UpdateInventoryItemCommand(
            registered.id(), "Issue222 Maintained Essence", "Updated", false, "222", "ESS"
        ));

        assertThat(updated.id()).isEqualTo(registered.id());
        assertThat(getInventoryItem.execute(registered.id())).isEqualTo(updated);
    }

    @Test
    void shouldRejectDuplicateCanonicalEssenceReferenceDuringMaintenance() {
        registerInventoryItem.execute(new RegisterInventoryItemCommand(
            "Issue222 Canonical Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, "223", null
        ));
        var candidate = registerInventoryItem.execute(new RegisterInventoryItemCommand(
            "Issue222 Candidate Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER
        ));

        assertThatThrownBy(() -> updateInventoryItem.execute(new UpdateInventoryItemCommand(
            candidate.id(), "Issue222 Candidate Essence", null, true, "223", null
        ))).isInstanceOf(InvalidInventoryItemMaintenanceException.class)
            .satisfies(error -> assertThat(((InvalidInventoryItemMaintenanceException) error).getCode())
                .isEqualTo("INVENTORY_ITEM_CANONICAL_ESSENCE_REFERENCE_CONFLICT"));
    }

    @Test
    void shouldAllowFinishedProductsToShareAnEssenceReferenceDuringMaintenance() {
        var first = registerInventoryItem.execute(new RegisterInventoryItemCommand(
            "Issue222 First Finished Product", null, Category.FINISHED_PRODUCT, UnitOfMeasure.UNIT
        ));
        var second = registerInventoryItem.execute(new RegisterInventoryItemCommand(
            "Issue222 Second Finished Product", null, Category.FINISHED_PRODUCT, UnitOfMeasure.UNIT
        ));

        var firstUpdated = updateInventoryItem.execute(new UpdateInventoryItemCommand(
            first.id(), first.name(), null, true, "224", null
        ));
        var secondUpdated = updateInventoryItem.execute(new UpdateInventoryItemCommand(
            second.id(), second.name(), null, true, "224", null
        ));

        assertThat(firstUpdated.essenceReference()).isEqualTo("224");
        assertThat(secondUpdated.essenceReference()).isEqualTo("224");
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

    @Test
    void shouldExposeCategorySetPaginationAndStableReferencesThroughPublicStockContract() {
        var firstId = new UUID(0, 230);
        var secondId = new UUID(0, 231);
        inventoryItemRepository.save(new InventoryItem(
            secondId, "Issue230 same name", null, Category.FINISHED_PRODUCT, UnitOfMeasure.UNIT, true,
            "230", "PKG", null
        ));
        inventoryItemRepository.save(new InventoryItem(
            firstId, "Issue230 same name", null, Category.FINISHED_PRODUCT, UnitOfMeasure.MILLILITER, true,
            "230", "BLK", null
        ));

        var result = inventoryItemStockLookup.findPage(new InventoryItemStockLookup.Query(
            List.of("FINISHED_PRODUCT", "ESSENCE", "FINISHED_PRODUCT"), 0, 100
        ));
        var allItems = inventoryItemStockLookup.findPage(new InventoryItemStockLookup.Query(List.of(), 0, 100));
        var finishedProducts = inventoryItemStockLookup.findPage(new InventoryItemStockLookup.Query(
            List.of("FINISHED_PRODUCT"), 0, 100
        ));

        var issueRows = result.content().stream()
            .filter(item -> item.id().equals(firstId) || item.id().equals(secondId))
            .toList();
        assertThat(issueRows).extracting(InventoryItemStockLookup.Item::id)
            .containsExactly(firstId, secondId);
        assertThat(issueRows).extracting(InventoryItemStockLookup.Item::essenceReference)
            .containsOnly("230");
        assertThat(issueRows).extracting(InventoryItemStockLookup.Item::productionTypeCode)
            .containsExactly("BLK", "PKG");
        assertThat(result.size()).isEqualTo(100);
        assertThat(allItems.content()).extracting(InventoryItemStockLookup.Item::id)
            .contains(firstId, secondId);
        assertThat(finishedProducts.content()).allMatch(item -> item.category().equals("FINISHED_PRODUCT"));
        assertThat(finishedProducts.content().stream()
            .filter(item -> item.id().equals(firstId) || item.id().equals(secondId)))
            .extracting(InventoryItemStockLookup.Item::id)
            .containsExactly(firstId, secondId);
    }
}
