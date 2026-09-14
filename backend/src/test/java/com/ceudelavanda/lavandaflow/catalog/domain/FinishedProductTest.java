package com.ceudelavanda.lavandaflow.catalog.domain;

import com.ceudelavanda.lavandaflow.catalog.ProductGender;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinishedProductTest {
    @ParameterizedTest
    @EnumSource(ProductGender.class)
    void shouldPreserveGenderForBulkAndPackagedProducts(ProductGender gender) {
        for (var unit : new UnitOfMeasure[]{UnitOfMeasure.MILLILITER, UnitOfMeasure.UNIT}) {
            var item = InventoryItem.create("Perfume", null, Category.FINISHED_PRODUCT, unit, "001", "PRF", gender);
            assertThat(item.getGender()).isEqualTo(gender);
            assertThat(ProductGender.fromCode(gender.code())).isEqualTo(gender);
            assertThat(item.getUnitOfMeasure()).isEqualTo(unit);
            assertThat(item.getEssenceReference()).isEqualTo("001");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "X", "m", "MC", "M /C", "F/ C"})
    void shouldRejectNonCanonicalGender(String code) {
        assertThatThrownBy(() -> ProductGender.fromCode(code)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = Category.class, names = {"ESSENCE", "FINISHED_PRODUCT"}, mode = EnumSource.Mode.EXCLUDE)
    void shouldRejectGenderOnIneligibleCategories(Category category) {
        assertThatThrownBy(() -> InventoryItem.create("Item", null, category, UnitOfMeasure.UNIT,
            null, null, ProductGender.SHARED)).isInstanceOf(InvalidProductGenderException.class);
        var item = InventoryItem.create("Perfume", null, Category.FINISHED_PRODUCT, UnitOfMeasure.UNIT,
            null, null, ProductGender.SHARED);
        assertThatThrownBy(() -> item.changeCategory(category)).isInstanceOf(InvalidProductGenderException.class);
        assertThat(item.getCategory()).isEqualTo(Category.FINISHED_PRODUCT);
    }

    @Test
    void shouldPreserveOneTimeReferencesAndPreventCanonicalReferenceRecycling() {
        var item = InventoryItem.create("Perfume", null, Category.FINISHED_PRODUCT, UnitOfMeasure.MILLILITER);
        assertThat(item.getGender()).isNull();
        assertThatThrownBy(() -> item.assignEssenceReference("000")).isInstanceOf(IllegalArgumentException.class);
        item.assignEssenceReference("999");
        item.assignEssenceReference("999");
        assertThatThrownBy(() -> item.assignEssenceReference("001")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> item.assignEssenceReference(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> item.changeCategory(Category.BOTTLE)).isInstanceOf(IllegalArgumentException.class);
        var essence = InventoryItem.create("Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, "001", null);
        assertThatThrownBy(() -> essence.changeCategory(Category.FINISHED_PRODUCT)).isInstanceOf(IllegalStateException.class);
    }
}
