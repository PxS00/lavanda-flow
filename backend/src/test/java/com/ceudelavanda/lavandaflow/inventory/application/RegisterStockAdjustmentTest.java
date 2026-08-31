package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.inventory.application.movement.RegisterStockAdjustment;
import com.ceudelavanda.lavandaflow.inventory.application.movement.RegisterStockAdjustmentCommand;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovement;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.BatchNotFoundException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InsufficientStockException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InvalidStockAdjustmentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterStockAdjustmentTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-08-25T16:00:00Z");

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    private RegisterStockAdjustment registerStockAdjustment;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(OCCURRED_AT, ZoneOffset.UTC);
        registerStockAdjustment = new RegisterStockAdjustment(
            batchRepository,
            stockMovementRepository,
            clock
        );
    }

    @Test
    void shouldRegisterPositiveStockAdjustment() {
        var batchId = UUID.randomUUID();
        var batch = spy(batchWithBalance(batchId, "100"));
        var command = new RegisterStockAdjustmentCommand(
            batchId,
            new BigDecimal("25"),
            "Physical count correction"
        );

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        var result = registerStockAdjustment.execute(command);

        var movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());

        var movement = movementCaptor.getValue();

        assertThat(movement.id()).isEqualTo(result.movementId());
        assertThat(movement.batchId()).isEqualTo(batchId);
        assertThat(movement.type()).isEqualTo(MovementType.ADJUSTMENT_IN);
        assertThat(movement.quantity()).isEqualByComparingTo("25");
        assertThat(movement.reason()).isEqualTo("Physical count correction");
        assertThat(movement.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(result.batchId()).isEqualTo(batchId);
        assertThat(result.type()).isEqualTo(MovementType.ADJUSTMENT_IN);
        assertThat(result.quantity()).isEqualByComparingTo("25");
        assertThat(result.resultingBalance()).isEqualByComparingTo("125");
        assertThat(result.reason()).isEqualTo("Physical count correction");
        assertThat(result.occurredAt()).isEqualTo(OCCURRED_AT);
        verify(batchRepository).findById(batchId);
        verify(batch).adjustQuantity(new BigDecimal("25"));
        verify(batchRepository).save(batch);
    }

    @Test
    void shouldRegisterNegativeStockAdjustmentWithPositiveMovementQuantity() {
        var batchId = UUID.randomUUID();
        var batch = spy(batchWithBalance(batchId, "100"));
        var command = new RegisterStockAdjustmentCommand(
            batchId,
            new BigDecimal("-25"),
            "Physical count correction"
        );

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        var result = registerStockAdjustment.execute(command);

        var movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());

        var movement = movementCaptor.getValue();

        assertThat(movement.type()).isEqualTo(MovementType.ADJUSTMENT_OUT);
        assertThat(movement.quantity()).isEqualByComparingTo("25");
        assertThat(result.type()).isEqualTo(MovementType.ADJUSTMENT_OUT);
        assertThat(result.quantity()).isEqualByComparingTo("25");
        assertThat(result.resultingBalance()).isEqualByComparingTo("75");
        verify(batch).adjustQuantity(new BigDecimal("-25"));
        verify(batchRepository).save(batch);
    }

    @Test
    void shouldRejectNullReasonBeforeLoadingOrPersisting() {
        assertReasonIsRejected(null);
    }

    @Test
    void shouldRejectBlankReasonBeforeLoadingOrPersisting() {
        assertReasonIsRejected("");
    }

    @Test
    void shouldRejectWhitespaceOnlyReasonBeforeLoadingOrPersisting() {
        assertReasonIsRejected("   ");
    }

    @Test
    void shouldRejectReasonLongerThanMaximumLengthWithoutPersisting() {
        var batchId = UUID.randomUUID();
        var batch = batchWithBalance(batchId, "100");
        var command = new RegisterStockAdjustmentCommand(
            batchId,
            new BigDecimal("25"),
            "a".repeat(256)
        );

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> registerStockAdjustment.execute(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("reason must not exceed 255 characters");

        verify(batchRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenBatchDoesNotExist() {
        var batchId = UUID.randomUUID();
        var command = new RegisterStockAdjustmentCommand(
            batchId,
            new BigDecimal("25"),
            "Physical count correction"
        );

        when(batchRepository.findById(batchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registerStockAdjustment.execute(command))
            .isInstanceOf(BatchNotFoundException.class)
            .satisfies(exception -> assertThat(
                ((BatchNotFoundException) exception).getBatchId()
            ).isEqualTo(batchId));

        verify(batchRepository).findById(batchId);
        verify(batchRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void shouldRejectZeroAdjustmentWithoutPersisting() {
        var batchId = UUID.randomUUID();
        var batch = batchWithBalance(batchId, "100");
        var command = new RegisterStockAdjustmentCommand(
            batchId,
            BigDecimal.ZERO,
            "Physical count correction"
        );

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> registerStockAdjustment.execute(command))
            .isInstanceOf(InvalidStockAdjustmentException.class)
            .satisfies(exception -> assertThat(
                ((InvalidStockAdjustmentException) exception).getCode()
            ).isEqualTo("INVALID_STOCK_ADJUSTMENT"));

        verify(batchRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void shouldPropagateInsufficientStockForNegativeAdjustmentWithoutPersisting() {
        var batchId = UUID.randomUUID();
        var batch = batchWithBalance(batchId, "100");
        var command = new RegisterStockAdjustmentCommand(
            batchId,
            new BigDecimal("-100.001"),
            "Physical count correction"
        );

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> registerStockAdjustment.execute(command))
            .isInstanceOf(InsufficientStockException.class)
            .satisfies(exception -> {
                var insufficientStock = (InsufficientStockException) exception;
                assertThat(insufficientStock.getRequestedQuantity())
                    .isEqualByComparingTo("100.001");
                assertThat(insufficientStock.getAvailableQuantity())
                    .isEqualByComparingTo("100");
            });

        assertThat(batch.getCurrentQuantity()).isEqualByComparingTo("100");
        verify(batchRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    private void assertReasonIsRejected(String reason) {
        var command = new RegisterStockAdjustmentCommand(
            UUID.randomUUID(),
            new BigDecimal("25"),
            reason
        );

        assertThatThrownBy(() -> registerStockAdjustment.execute(command))
            .isInstanceOf(InvalidStockAdjustmentException.class)
            .hasMessage("Stock adjustment reason must not be blank");

        verify(batchRepository, never()).findById(any());
        verify(batchRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    private static Batch batchWithBalance(UUID batchId, String balance) {
        return new Batch(
            batchId,
            UUID.randomUUID(),
            null,
            "LOT-001",
            new BigDecimal(balance),
            new BigDecimal(balance),
            LocalDate.of(2026, 8, 25),
            null
        );
    }
}
