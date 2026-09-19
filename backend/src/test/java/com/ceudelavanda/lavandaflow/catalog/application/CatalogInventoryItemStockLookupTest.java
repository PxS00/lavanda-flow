package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemStockLookup;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogInventoryItemStockLookupTest {

    @Mock private InventoryItemQuery inventoryItemQuery;
    private InventoryItemStockLookup lookup;

    @BeforeEach
    void setUp() {
        lookup = new CatalogInventoryItemStockLookup(inventoryItemQuery);
    }

    @Test
    void shouldDeduplicateCategorySetAndPreservePageOrderAndMetadata() {
        var first = item("Álcool", Category.ALCOHOL, null, "BULK");
        var second = item("Base", Category.BASE, "027", null);
        when(inventoryItemQuery.findStockPage(Set.of(Category.BASE, Category.ALCOHOL), 1, 2))
            .thenReturn(new InventoryItemPage(List.of(first, second), 1, 2, 4, 2));

        var result = lookup.findPage(new InventoryItemStockLookup.Query(
            List.of("BASE", "ALCOHOL", "BASE"), 1, 2
        ));

        assertThat(result.content()).extracting(InventoryItemStockLookup.Item::id)
            .containsExactly(first.id(), second.id());
        assertThat(result.content().get(0).productionTypeCode()).isEqualTo("BULK");
        assertThat(result.content().get(1).essenceReference()).isEqualTo("027");
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(4);
    }

    @Test
    void shouldUseNoCategoryRestrictionAndRejectUnknownCategory() {
        when(inventoryItemQuery.findStockPage(Set.of(), 0, 20))
            .thenReturn(new InventoryItemPage(List.of(), 0, 20, 0, 0));

        assertThat(lookup.findPage(new InventoryItemStockLookup.Query(List.of(), 0, 20)).content()).isEmpty();
        assertThatThrownBy(() -> lookup.findPage(
            new InventoryItemStockLookup.Query(List.of("UNKNOWN"), 0, 20)
        )).isInstanceOf(InvalidInventoryItemSearchQueryException.class);
    }

    private static InventoryItemResult item(
        String name, Category category, String essenceReference, String productionTypeCode
    ) {
        return new InventoryItemResult(
            UUID.randomUUID(), name, null, category, UnitOfMeasure.MILLILITER, true,
            essenceReference, productionTypeCode
        );
    }
}
