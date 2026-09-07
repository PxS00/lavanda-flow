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
        assertThat(item.getEssenceReference()).isNull();
        assertThat(item.getProductionTypeCode()).isNull();
    }

    @Test
    void shouldCreateApplicableProductionReferenceMetadata() {
        var firstEssence = InventoryItem.create(
            "First Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, "001", null
        );
        var lastEssence = InventoryItem.create(
            "Last Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, "999", null
        );
        var bodySplash = InventoryItem.create(
            "Body Splash", null, Category.OTHER, UnitOfMeasure.MILLILITER, null, "BDS"
        );

        assertThat(firstEssence.getEssenceReference()).isEqualTo("001");
        assertThat(lastEssence.getEssenceReference()).isEqualTo("999");
        assertThat(bodySplash.getProductionTypeCode()).isEqualTo("BDS");
    }

    @Test
    void shouldRejectInvalidProductionReferenceMetadata() {
        assertThatThrownBy(() -> InventoryItem.create(
            "Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, "000", null
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("essenceReference must be a three-digit value from 001 through 999");

        assertThatThrownBy(() -> InventoryItem.create(
            "Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, "1000", null
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("essenceReference must be a three-digit value from 001 through 999");

        assertThatThrownBy(() -> InventoryItem.create(
            "Body Splash", null, Category.OTHER, UnitOfMeasure.MILLILITER, null, "bds"
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("productionTypeCode must be exactly three uppercase letters");
    }

    @Test
    void shouldRejectEssenceReferenceForNonEssenceItem() {
        assertThatThrownBy(() -> InventoryItem.create(
            "Bottle", null, Category.BOTTLE, UnitOfMeasure.UNIT, "014", null
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("essenceReference is only valid for ESSENCE items");
    }

    @Test
    void shouldAllowOneTimeProductionReferenceAssignment() {
        var item = InventoryItem.create(
            "Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER
        );

        item.assignEssenceReference("001");
        item.assignEssenceReference("001");
        item.assignProductionTypeCode("ESS");
        item.assignProductionTypeCode("ESS");

        assertThat(item.getEssenceReference()).isEqualTo("001");
        assertThat(item.getProductionTypeCode()).isEqualTo("ESS");
        assertThatThrownBy(() -> item.assignEssenceReference("002"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("assigned essenceReference cannot be changed");
        assertThatThrownBy(() -> item.assignEssenceReference(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("assigned essenceReference cannot be changed");
        assertThatThrownBy(() -> item.assignProductionTypeCode("BDS"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("assigned productionTypeCode cannot be changed");
        assertThatThrownBy(() -> item.assignProductionTypeCode(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("assigned productionTypeCode cannot be changed");
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
            "Old Name", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, "014", null
        );

        item.rename("New Name");

        assertThat(item.getName()).isEqualTo("New Name");
        assertThat(item.getEssenceReference()).isEqualTo("014");
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
    void shouldPreventChangingAnEssenceReferenceItemToAnotherCategory() {
        var item = InventoryItem.create(
            "Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, "014", null
        );

        assertThatThrownBy(() -> item.changeCategory(Category.CHEMICAL_INPUT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("essenceReference is only valid for ESSENCE items");
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
