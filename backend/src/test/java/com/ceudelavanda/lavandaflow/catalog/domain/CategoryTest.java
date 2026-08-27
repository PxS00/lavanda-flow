package com.ceudelavanda.lavandaflow.catalog.domain;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryTest {

    @Test
    void shouldContainApprovedCategories() {
        assertThat(Set.of(Category.values()))
            .containsExactlyInAnyOrder(
                Category.ESSENCE,
                Category.CHEMICAL_INPUT,
                Category.BASE,
                Category.ALCOHOL,
                Category.COLORANT,
                Category.FIXATIVE,
                Category.BOTTLE,
                Category.VALVE,
                Category.CAP,
                Category.LABEL,
                Category.PACKAGING,
                Category.OTHER
            );
    }

    @Test
    void shouldRejectUnsupportedCategory() {
        assertThatThrownBy(() -> Category.valueOf("PERFUME"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
