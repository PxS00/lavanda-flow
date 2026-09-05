package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogInventoryItemLookupTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Test
    void shouldRetrieveAllActiveItemsInOneCatalogRead() {
        var first = InventoryItem.create("First", null, Category.ESSENCE, UnitOfMeasure.MILLILITER);
        var second = InventoryItem.create("Second", null, Category.BOTTLE, UnitOfMeasure.UNIT);
        when(inventoryItemRepository.findAllActive()).thenReturn(List.of(first, second));

        var snapshots = new CatalogInventoryItemLookup(inventoryItemRepository).findAllActive();

        verify(inventoryItemRepository).findAllActive();
        assertThat(snapshots).extracting(snapshot -> snapshot.id())
            .containsExactly(first.getId(), second.getId());
        assertThat(snapshots).allMatch(snapshot -> snapshot.active());
    }

    @Test
    void shouldRetrieveRequestedItemsInOneBulkLookup() {
        var first = InventoryItem.create("First", null, Category.ESSENCE, UnitOfMeasure.MILLILITER);
        var second = InventoryItem.create("Second", null, Category.BOTTLE, UnitOfMeasure.UNIT);
        second.deactivate();
        var ids = List.of(first.getId(), second.getId());
        when(inventoryItemRepository.findByIds(ids)).thenReturn(List.of(first, second));

        var snapshots = new CatalogInventoryItemLookup(inventoryItemRepository).findByIds(ids);

        verify(inventoryItemRepository).findByIds(ids);
        assertThat(snapshots).extracting(snapshot -> snapshot.id())
            .containsExactly(first.getId(), second.getId());
        assertThat(snapshots.get(0).name()).isEqualTo("First");
        assertThat(snapshots.get(0).unitOfMeasure()).isEqualTo(UnitOfMeasure.MILLILITER);
        assertThat(snapshots.get(1).active()).isFalse();
    }
}
