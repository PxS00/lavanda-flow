package com.ceudelavanda.lavandaflow.inventory.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockQuantityRulesTest {

    @Test
    void shouldAcceptSmallestPositiveQuantity() {
        var quantity = StockQuantityRules.requirePositive(
            new BigDecimal("0.000001"),
            "quantity"
        );

        assertThat(quantity).isEqualByComparingTo("0.000001");
        assertThat(quantity.scale()).isEqualTo(6);
    }

    @Test
    void shouldAcceptMaximumRepresentableQuantity() {
        var quantity = StockQuantityRules.requirePositive(
            new BigDecimal("9999999999999.999999"),
            "quantity"
        );

        assertThat(quantity).isEqualByComparingTo("9999999999999.999999");
    }

    @Test
    void shouldRejectQuantityThatRequiresRounding() {
        assertThatThrownBy(() -> StockQuantityRules.requirePositive(
            new BigDecimal("1.0000004"),
            "quantity"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("NUMERIC(19,6)");
    }

    @Test
    void shouldRejectQuantityBelowSupportedScale() {
        assertThatThrownBy(() -> StockQuantityRules.requirePositive(
            new BigDecimal("0.0000001"),
            "quantity"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("NUMERIC(19,6)");
    }

    @Test
    void shouldRejectQuantityWithTooManyIntegerDigits() {
        assertThatThrownBy(() -> StockQuantityRules.requirePositive(
            new BigDecimal("10000000000000"),
            "quantity"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("NUMERIC(19,6)");
    }
}
