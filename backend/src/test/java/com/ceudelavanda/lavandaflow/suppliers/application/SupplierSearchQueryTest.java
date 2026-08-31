package com.ceudelavanda.lavandaflow.suppliers.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplierSearchQueryTest {

    @Test
    void shouldNormalizeOptionalNameFilter() {
        var query = new SupplierSearchQuery("  aromas  ", null, 0, 20);
        var blankQuery = new SupplierSearchQuery("   ", null, 0, 20);

        assertThat(query.name()).isEqualTo("aromas");
        assertThat(blankQuery.name()).isNull();
    }

    @Test
    void shouldRejectInvalidPagination() {
        assertThatThrownBy(() -> new SupplierSearchQuery(null, null, -1, 20))
            .isInstanceOf(InvalidSupplierSearchQueryException.class);
        assertThatThrownBy(() -> new SupplierSearchQuery(null, null, 0, 0))
            .isInstanceOf(InvalidSupplierSearchQueryException.class);
        assertThatThrownBy(() -> new SupplierSearchQuery(null, null, 0, 101))
            .isInstanceOf(InvalidSupplierSearchQueryException.class);
    }
}
