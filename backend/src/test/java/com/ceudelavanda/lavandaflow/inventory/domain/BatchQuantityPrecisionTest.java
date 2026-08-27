package com.ceudelavanda.lavandaflow.inventory.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchQuantityPrecisionTest {

    @Test
    void shouldRejectEntryWhenResultingBalanceExceedsSupportedPrecision() {
        var maximum = new BigDecimal("9999999999999.999999");
        var batch = Batch.create(
            UUID.randomUUID(),
            null,
            "MAX-001",
            maximum,
            LocalDate.of(2026, 8, 27),
            null
        );

        assertThatThrownBy(() -> batch.addQuantity(new BigDecimal("0.000001")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("NUMERIC(19,6)");

        assertThat(batch.getCurrentQuantity()).isEqualByComparingTo(maximum);
    }
}
