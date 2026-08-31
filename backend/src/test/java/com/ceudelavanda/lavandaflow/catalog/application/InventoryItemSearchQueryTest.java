package com.ceudelavanda.lavandaflow.catalog.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryItemSearchQueryTest {

    @Test
    void shouldNormalizeOptionalNameFilter() {
        var query = new InventoryItemSearchQuery("  lavender  ", null, null, 0, 20);
        var blankQuery = new InventoryItemSearchQuery("   ", null, null, 0, 20);

        assertThat(query.name()).isEqualTo("lavender");
        assertThat(blankQuery.name()).isNull();
    }

    @Test
    void shouldRejectNegativePage() {
        assertThatThrownBy(() -> new InventoryItemSearchQuery(null, null, null, -1, 20))
            .isInstanceOf(InvalidInventoryItemSearchQueryException.class)
            .satisfies(exception -> assertThat(
                ((InvalidInventoryItemSearchQueryException) exception).getDetails()
            ).containsEntry("page", "must be zero or positive"));
    }

    @Test
    void shouldRejectInvalidPageSize() {
        assertThatThrownBy(() -> new InventoryItemSearchQuery(null, null, null, 0, 0))
            .isInstanceOf(InvalidInventoryItemSearchQueryException.class);

        assertThatThrownBy(() -> new InventoryItemSearchQuery(null, null, null, 0, 101))
            .isInstanceOf(InvalidInventoryItemSearchQueryException.class);
    }
}
