package com.ceudelavanda.lavandaflow.inventory.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockMovementTest {

    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-24T12:00:00Z");

    @Test
    void shouldCreateImmutableStockMovement() {
        var movement = StockMovement.create(
            BATCH_ID,
            MovementType.ENTRY,
            new BigDecimal("1500.500"),
            "Initial receipt",
            OCCURRED_AT
        );

        assertThat(movement.getId()).isNotNull();
        assertThat(movement.getBatchId()).isEqualTo(BATCH_ID);
        assertThat(movement.getType()).isEqualTo(MovementType.ENTRY);
        assertThat(movement.getQuantity()).isEqualByComparingTo("1500.500");
        assertThat(movement.getReason()).isEqualTo("Initial receipt");
        assertThat(movement.getOccurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void shouldNormalizeBlankReasonToNull() {
        var movement = StockMovement.create(
            BATCH_ID,
            MovementType.CONSUMPTION,
            BigDecimal.ONE,
            "   ",
            OCCURRED_AT
        );

        assertThat(movement.getReason()).isNull();
    }

    @Test
    void shouldRejectNullBatchId() {
        assertThatThrownBy(() -> StockMovement.create(
            null,
            MovementType.ENTRY,
            BigDecimal.ONE,
            null,
            OCCURRED_AT
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("batchId must not be null");
    }

    @Test
    void shouldRejectNullMovementType() {
        assertThatThrownBy(() -> StockMovement.create(
            BATCH_ID,
            null,
            BigDecimal.ONE,
            null,
            OCCURRED_AT
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("type must not be null");
    }

    @Test
    void shouldRejectNonPositiveQuantity() {
        assertThatThrownBy(() -> StockMovement.create(
            BATCH_ID,
            MovementType.ENTRY,
            BigDecimal.ZERO,
            null,
            OCCURRED_AT
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("quantity must be greater than zero");
    }

    @Test
    void shouldRejectNullOccurredAt() {
        assertThatThrownBy(() -> StockMovement.create(
            BATCH_ID,
            MovementType.ENTRY,
            BigDecimal.ONE,
            null,
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("occurredAt must not be null");
    }

    @Test
    void shouldRestoreMovementWithExistingIdentifier() {
        var id = UUID.randomUUID();

        var movement = new StockMovement(
            id,
            BATCH_ID,
            MovementType.LOSS,
            BigDecimal.ONE,
            null,
            OCCURRED_AT
        );

        assertThat(movement.getId()).isEqualTo(id);
    }
}
