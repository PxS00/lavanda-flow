package com.ceudelavanda.lavandaflow.catalog.domain;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryItemTest {

    @Test
    void shouldCreateActiveInventoryItem() {
        var item = InventoryItem.create(
            "Good Girl Essence",
            "Fragrance essence",
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        );

        assertThat(item.getId()).isNotNull();
        assertThat(item.getName()).isEqualTo("Good Girl Essence");
        assertThat(item.getDescription()).isEqualTo("Fragrance essence");
        assertThat(item.getCategory()).isEqualTo(Category.ESSENCE);
        assertThat(item.getUnitOfMeasure()).isEqualTo(UnitOfMeasure.MILLILITER);
        assertThat(item.isActive()).isTrue();
    }

    @Test
    void shouldTrimNameWhenCreating() {
        var item = InventoryItem.create(
            "  Good Girl Essence  ",
            null,
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        );

        assertThat(item.getName()).isEqualTo("Good Girl Essence");
    }

    @Test
    void shouldNormalizeBlankDescriptionToNull() {
        var item = InventoryItem.create(
            "Good Girl Essence",
            "   ",
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        );

        assertThat(item.getDescription()).isNull();
    }

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> InventoryItem.create(
            "   ",
            null,
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("name must not be blank");
    }

    @Test
    void shouldRejectNullCategory() {
        assertThatThrownBy(() -> InventoryItem.create(
            "Good Girl Essence",
            null,
            null,
            UnitOfMeasure.MILLILITER
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("category must not be null");
    }

    @Test
    void shouldRejectNullUnitOfMeasure() {
        assertThatThrownBy(() -> InventoryItem.create(
            "Good Girl Essence",
            null,
            Category.ESSENCE,
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("unitOfMeasure must not be null");
    }

    @Test
    void shouldRenameInventoryItem() {
        var item = InventoryItem.create(
            "Old Name",
            null,
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        );

        item.rename("New Name");

        assertThat(item.getName()).isEqualTo("New Name");
    }

    @Test
    void shouldChangeDescription() {
        var item = InventoryItem.create(
            "Good Girl Essence",
            null,
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        );

        item.changeDescription("Updated description");

        assertThat(item.getDescription()).isEqualTo("Updated description");
    }

    @Test
    void shouldChangeCategory() {
        var item = InventoryItem.create(
            "Bottle",
            null,
            Category.OTHER,
            UnitOfMeasure.UNIT
        );

        item.changeCategory(Category.BOTTLE);

        assertThat(item.getCategory()).isEqualTo(Category.BOTTLE);
    }

    @Test
    void shouldChangeUnitOfMeasure() {
        var item = InventoryItem.create(
            "Alcohol",
            null,
            Category.ALCOHOL,
            UnitOfMeasure.LITER
        );

        item.changeUnitOfMeasure(UnitOfMeasure.MILLILITER);

        assertThat(item.getUnitOfMeasure()).isEqualTo(UnitOfMeasure.MILLILITER);
    }

    @Test
    void shouldDeactivateAndActivateInventoryItem() {
        var item = InventoryItem.create(
            "Bottle",
            null,
            Category.BOTTLE,
            UnitOfMeasure.UNIT
        );

        item.deactivate();

        assertThat(item.isActive()).isFalse();

        item.activate();

        assertThat(item.isActive()).isTrue();
    }

    @Test
    void shouldRestoreInventoryItemWithExistingIdentifier() {
        var id = UUID.randomUUID();

        var item = new InventoryItem(
            id,
            "Good Girl Essence",
            null,
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER,
            false
        );

        assertThat(item.getId()).isEqualTo(id);
        assertThat(item.isActive()).isFalse();
    }
}
