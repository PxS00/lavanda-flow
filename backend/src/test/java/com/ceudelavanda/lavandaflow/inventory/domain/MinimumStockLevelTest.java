package com.ceudelavanda.lavandaflow.inventory.domain;

import com.ceudelavanda.lavandaflow.inventory.domain.exception.InvalidMinimumStockQuantityException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MinimumStockLevelTest {

    @Test
    void shouldCanonicalizeMinimumQuantityToScaleSix() {
        var level = new MinimumStockLevel(UUID.randomUUID(), new BigDecimal("250"));

        assertThat(level.getMinimumQuantity()).isEqualByComparingTo("250.000000");
        assertThat(level.getMinimumQuantity().scale()).isEqualTo(6);
    }

    @Test
    void shouldAcceptTrailingZerosBeyondScaleSixWithoutRounding() {
        var level = new MinimumStockLevel(UUID.randomUUID(), new BigDecimal("1.2300000"));

        assertThat(level.getMinimumQuantity()).isEqualByComparingTo("1.230000");
        assertThat(level.getMinimumQuantity().scale()).isEqualTo(6);
    }

    @Test
    void shouldRejectNullZeroNegativeAndMeaningfulExcessScale() {
        assertThatThrownBy(() -> new MinimumStockLevel(UUID.randomUUID(), null))
            .isInstanceOf(InvalidMinimumStockQuantityException.class);
        assertThatThrownBy(() -> new MinimumStockLevel(UUID.randomUUID(), BigDecimal.ZERO))
            .isInstanceOf(InvalidMinimumStockQuantityException.class);
        assertThatThrownBy(() -> new MinimumStockLevel(UUID.randomUUID(), new BigDecimal("-1")))
            .isInstanceOf(InvalidMinimumStockQuantityException.class);
        assertThatThrownBy(() -> new MinimumStockLevel(UUID.randomUUID(), new BigDecimal("1.0000001")))
            .isInstanceOf(InvalidMinimumStockQuantityException.class);
    }

    @Test
    void shouldRejectNullInventoryItemAndChangeQuantity() {
        assertThatThrownBy(() -> new MinimumStockLevel(null, BigDecimal.ONE))
            .isInstanceOf(IllegalArgumentException.class);

        var level = new MinimumStockLevel(UUID.randomUUID(), BigDecimal.ONE);
        level.changeMinimumQuantity(new BigDecimal("2.5"));

        assertThat(level.getMinimumQuantity()).isEqualByComparingTo("2.500000");
    }
}
