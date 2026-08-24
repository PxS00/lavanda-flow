package com.ceudelavanda.lavandaflow.inventory.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchTest {

    private static final UUID INVENTORY_ITEM_ID = UUID.randomUUID();
    private static final UUID SUPPLIER_ID = UUID.randomUUID();
    private static final LocalDate RECEIVED_AT = LocalDate.of(2026, 8, 24);

    @Test
    void shouldCreateBatchWithInitialBalance() {
        var batch = Batch.create(
            INVENTORY_ITEM_ID,
            SUPPLIER_ID,
            "GG-2026-01",
            new BigDecimal("1500.500"),
            RECEIVED_AT,
            LocalDate.of(2027, 10, 1)
        );

        assertThat(batch.getId()).isNotNull();
        assertThat(batch.getInventoryItemId()).isEqualTo(INVENTORY_ITEM_ID);
        assertThat(batch.getSupplierId()).isEqualTo(SUPPLIER_ID);
        assertThat(batch.getLotCode()).isEqualTo("GG-2026-01");
        assertThat(batch.getInitialQuantity()).isEqualByComparingTo("1500.500");
        assertThat(batch.getCurrentQuantity()).isEqualByComparingTo("1500.500");
        assertThat(batch.getReceivedAt()).isEqualTo(RECEIVED_AT);
        assertThat(batch.getExpiresAt()).isEqualTo(LocalDate.of(2027, 10, 1));
    }

    @Test
    void shouldAllowBatchWithoutSupplierLotCodeOrExpirationDate() {
        var batch = Batch.create(
            INVENTORY_ITEM_ID,
            null,
            "   ",
            BigDecimal.ONE,
            RECEIVED_AT,
            null
        );

        assertThat(batch.getSupplierId()).isNull();
        assertThat(batch.getLotCode()).isNull();
        assertThat(batch.getExpiresAt()).isNull();
    }

    @Test
    void shouldRejectNullInventoryItemId() {
        assertThatThrownBy(() -> Batch.create(
            null,
            null,
            null,
            BigDecimal.ONE,
            RECEIVED_AT,
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("inventoryItemId must not be null");
    }

    @Test
    void shouldRejectNonPositiveInitialQuantity() {
        assertThatThrownBy(() -> Batch.create(
            INVENTORY_ITEM_ID,
            null,
            null,
            BigDecimal.ZERO,
            RECEIVED_AT,
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("initialQuantity must be greater than zero");
    }

    @Test
    void shouldRejectNegativeCurrentQuantityWhenRestoring() {
        assertThatThrownBy(() -> new Batch(
            UUID.randomUUID(),
            INVENTORY_ITEM_ID,
            null,
            null,
            BigDecimal.ONE,
            new BigDecimal("-0.001"),
            RECEIVED_AT,
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("currentQuantity must not be negative");
    }

    @Test
    void shouldRejectNegativeCurrentQuantityWhenChangingBalance() {
        var batch = Batch.create(
            INVENTORY_ITEM_ID,
            null,
            null,
            BigDecimal.ONE,
            RECEIVED_AT,
            null
        );

        assertThatThrownBy(() -> batch.changeCurrentQuantity(new BigDecimal("-0.001")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("currentQuantity must not be negative");
    }

    @Test
    void shouldAllowCurrentQuantityGreaterThanInitialQuantityForFutureAdjustments() {
        var batch = Batch.create(
            INVENTORY_ITEM_ID,
            null,
            null,
            BigDecimal.ONE,
            RECEIVED_AT,
            null
        );

        batch.changeCurrentQuantity(new BigDecimal("2.000"));

        assertThat(batch.getCurrentQuantity()).isEqualByComparingTo("2.000");
    }

    @Test
    void shouldRejectNullReceivedAt() {
        assertThatThrownBy(() -> Batch.create(
            INVENTORY_ITEM_ID,
            null,
            null,
            BigDecimal.ONE,
            null,
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("receivedAt must not be null");
    }
}
