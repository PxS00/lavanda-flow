package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetInventoryItemTest {

    @Mock
    private InventoryItemQuery inventoryItemQuery;

    @Test
    void shouldReturnInventoryItem() {
        var itemId = UUID.randomUUID();
        var expected = new InventoryItemResult(
            itemId, "Lavender Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, true
        );
        when(inventoryItemQuery.findById(itemId)).thenReturn(Optional.of(expected));

        var result = new GetInventoryItem(inventoryItemQuery).execute(itemId);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void shouldThrowStandardizedNotFoundError() {
        var itemId = UUID.randomUUID();
        when(inventoryItemQuery.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new GetInventoryItem(inventoryItemQuery).execute(itemId))
            .isInstanceOf(InventoryItemNotFoundException.class)
            .satisfies(exception -> {
                var notFound = (InventoryItemNotFoundException) exception;
                assertThat(notFound.getCode()).isEqualTo("INVENTORY_ITEM_NOT_FOUND");
                assertThat(notFound.getDetails()).containsEntry("inventoryItemId", itemId.toString());
            });
    }
}
