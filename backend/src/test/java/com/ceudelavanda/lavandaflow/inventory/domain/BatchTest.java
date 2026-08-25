package com.ceudelavanda.lavandaflow.inventory.domain;

import com.ceudelavanda.lavandaflow.inventory.domain.exception.InsufficientStockException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InvalidStockAdjustmentException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;
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

    @Test
    void shouldIncreaseCurrentQuantityWhenAddingPositiveQuantity() {
        var batch = new Batch(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "LOT-001",
            new BigDecimal("100"),
            new BigDecimal("100"),
            LocalDate.now(),
            null
        );

        batch.addQuantity(new BigDecimal("50"));

        assertThat(batch.getCurrentQuantity())
            .isEqualByComparingTo("150");
    }

    @Test
    void shouldRejectZeroQuantity() {
        var batch = Batch.create(
            INVENTORY_ITEM_ID,
            null,
            null,
            BigDecimal.ONE,
            RECEIVED_AT,
            null
        );

        assertThatThrownBy(() -> batch.addQuantity(BigDecimal.ZERO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("quantity must be greater than zero");
    }

    @Test
    void shouldRejectNegativeQuantity() {
        var batch = Batch.create(
            INVENTORY_ITEM_ID,
            null,
            null,
            BigDecimal.ONE,
            RECEIVED_AT,
            null
        );

        assertThatThrownBy(() -> batch.addQuantity(new BigDecimal("-0.001")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("quantity must be greater than zero");
    }

    @Test
    void shouldRejectNullQuantity() {
        var batch = Batch.create(
            INVENTORY_ITEM_ID,
            null,
            null,
            BigDecimal.ONE,
            RECEIVED_AT,
            null
        );

        assertThatThrownBy(() -> batch.addQuantity(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("quantity must be greater than zero");
    }

    @Test
    void shouldDecreaseCurrentQuantityWhenRemovingPositiveQuantity() {
        var batch = batchWithBalance("100");

        batch.removeQuantity(new BigDecimal("40"));

        assertThat(batch.getCurrentQuantity()).isEqualByComparingTo("60");
    }

    @Test
    void shouldRejectNullQuantityWhenRemoving() {
        var batch = batchWithBalance("100");

        assertThatThrownBy(() -> batch.removeQuantity(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("quantity must be greater than zero");
    }

    @Test
    void shouldRejectZeroQuantityWhenRemoving() {
        var batch = batchWithBalance("100");

        assertThatThrownBy(() -> batch.removeQuantity(BigDecimal.ZERO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("quantity must be greater than zero");
    }

    @Test
    void shouldRejectNegativeQuantityWhenRemoving() {
        var batch = batchWithBalance("100");

        assertThatThrownBy(() -> batch.removeQuantity(new BigDecimal("-0.001")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("quantity must be greater than zero");
    }

    @Test
    void shouldRejectWithdrawalGreaterThanAvailableBalanceWithoutMutatingBalance() {
        var batch = batchWithBalance("100");

        assertThatThrownBy(() -> batch.removeQuantity(new BigDecimal("100.001")))
            .isInstanceOf(InsufficientStockException.class)
            .satisfies(exception -> {
                var insufficientStock = (InsufficientStockException) exception;
                assertThat(insufficientStock.getBatchId()).isEqualTo(batch.getId());
                assertThat(insufficientStock.getRequestedQuantity())
                    .isEqualByComparingTo("100.001");
                assertThat(insufficientStock.getAvailableQuantity())
                    .isEqualByComparingTo("100");
            });

        assertThat(batch.getCurrentQuantity()).isEqualByComparingTo("100");
    }

    @Test
    void shouldAllowWithdrawingExactlyTheAvailableBalance() {
        var batch = batchWithBalance("100");

        batch.removeQuantity(new BigDecimal("100"));

        assertThat(batch.getCurrentQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldIncreaseCurrentQuantityWhenAdjustingByPositiveDecimalQuantity() {
        var batch = batchWithBalance("100.125");

        batch.adjustQuantity(new BigDecimal("25.375"));

        assertThat(batch.getCurrentQuantity()).isEqualByComparingTo("125.500");
    }

    @Test
    void shouldDecreaseCurrentQuantityWhenAdjustingByNegativeQuantity() {
        var batch = batchWithBalance("100");

        batch.adjustQuantity(new BigDecimal("-25"));

        assertThat(batch.getCurrentQuantity()).isEqualByComparingTo("75");
    }

    @Test
    void shouldAllowNegativeAdjustmentEqualToAvailableBalance() {
        var batch = batchWithBalance("100");

        batch.adjustQuantity(new BigDecimal("-100"));

        assertThat(batch.getCurrentQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldRejectNullStockAdjustment() {
        var batch = batchWithBalance("100");

        assertThatThrownBy(() -> batch.adjustQuantity(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("adjustment must not be null");
    }

    @Test
    void shouldRejectZeroStockAdjustment() {
        var batch = batchWithBalance("100");

        assertThatThrownBy(() -> batch.adjustQuantity(BigDecimal.ZERO))
            .isInstanceOf(InvalidStockAdjustmentException.class)
            .satisfies(exception -> {
                var invalidAdjustment = (InvalidStockAdjustmentException) exception;
                assertThat(invalidAdjustment.getCode())
                    .isEqualTo("INVALID_STOCK_ADJUSTMENT");
                assertThat(invalidAdjustment.getErrorType())
                    .isEqualTo(ErrorType.VALIDATION);
            });
    }

    @Test
    void shouldRejectExcessiveNegativeAdjustmentWithoutMutatingBalance() {
        var batch = batchWithBalance("100");

        assertThatThrownBy(() -> batch.adjustQuantity(new BigDecimal("-100.001")))
            .isInstanceOf(InsufficientStockException.class)
            .satisfies(exception -> {
                var insufficientStock = (InsufficientStockException) exception;
                assertThat(insufficientStock.getRequestedQuantity())
                    .isEqualByComparingTo("100.001");
                assertThat(insufficientStock.getAvailableQuantity())
                    .isEqualByComparingTo("100");
            });

        assertThat(batch.getCurrentQuantity()).isEqualByComparingTo("100");
    }

    private static Batch batchWithBalance(String balance) {
        return new Batch(
            UUID.randomUUID(),
            INVENTORY_ITEM_ID,
            null,
            "LOT-001",
            new BigDecimal(balance),
            new BigDecimal(balance),
            RECEIVED_AT,
            null
        );
    }

}
