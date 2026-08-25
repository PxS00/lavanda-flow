package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterStockWithdrawalCommand;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovement;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.BatchNotFoundException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InsufficientStockException;
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
class RegisterStockWithdrawalTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-08-25T16:00:00Z");

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    private RegisterStockWithdrawal registerStockWithdrawal;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(OCCURRED_AT, ZoneOffset.UTC);
        registerStockWithdrawal = new RegisterStockWithdrawal(
            batchRepository,
            stockMovementRepository,
            clock
        );
    }

    @Test
    void shouldRegisterStockWithdrawal() {
        var batchId = UUID.randomUUID();
        var batch = spy(batchWithBalance(batchId, "100"));
        var command = new RegisterStockWithdrawalCommand(
            batchId,
            new BigDecimal("40"),
            "Inventory use"
        );

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        var result = registerStockWithdrawal.execute(command);

        var movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());

        var movement = movementCaptor.getValue();

        assertThat(movement.id()).isEqualTo(result.movementId());
        assertThat(movement.batchId()).isEqualTo(batchId);
        assertThat(movement.type()).isEqualTo(MovementType.CONSUMPTION);
        assertThat(movement.quantity()).isEqualByComparingTo("40");
        assertThat(movement.reason()).isEqualTo("Inventory use");
        assertThat(movement.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(result.batchId()).isEqualTo(batchId);
        assertThat(result.type()).isEqualTo(MovementType.CONSUMPTION);
        assertThat(result.quantity()).isEqualByComparingTo("40");
        assertThat(result.resultingBalance()).isEqualByComparingTo("60");
        assertThat(result.reason()).isEqualTo("Inventory use");
        assertThat(result.occurredAt()).isEqualTo(OCCURRED_AT);
        verify(batchRepository).findById(batchId);
        verify(batch).removeQuantity(new BigDecimal("40"));
        verify(batchRepository).save(batch);
    }

    @Test
    void shouldNormalizeBlankReasonUsingStockMovementRules() {
        var batchId = UUID.randomUUID();
        var batch = batchWithBalance(batchId, "100");
        var command = new RegisterStockWithdrawalCommand(
            batchId,
            new BigDecimal("40"),
            "   "
        );

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        var result = registerStockWithdrawal.execute(command);

        var movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());

        assertThat(movementCaptor.getValue().reason()).isNull();
        assertThat(result.reason()).isNull();
    }

    @Test
    void shouldThrowWhenBatchDoesNotExist() {
        var batchId = UUID.randomUUID();
        var command = new RegisterStockWithdrawalCommand(
            batchId,
            new BigDecimal("40"),
            null
        );

        when(batchRepository.findById(batchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registerStockWithdrawal.execute(command))
            .isInstanceOf(BatchNotFoundException.class)
            .satisfies(exception -> assertThat(
                ((BatchNotFoundException) exception).getBatchId()
            ).isEqualTo(batchId));

        verify(batchRepository).findById(batchId);
        verify(batchRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void shouldPropagateInsufficientStockWithoutPersisting() {
        var batchId = UUID.randomUUID();
        var batch = batchWithBalance(batchId, "100");
        var command = new RegisterStockWithdrawalCommand(
            batchId,
            new BigDecimal("100.001"),
            "Inventory use"
        );

        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> registerStockWithdrawal.execute(command))
            .isInstanceOf(InsufficientStockException.class)
            .satisfies(exception -> {
                var insufficientStock = (InsufficientStockException) exception;
                assertThat(insufficientStock.getBatchId()).isEqualTo(batchId);
                assertThat(insufficientStock.getRequestedQuantity())
                    .isEqualByComparingTo("100.001");
                assertThat(insufficientStock.getAvailableQuantity())
                    .isEqualByComparingTo("100");
            });

        assertThat(batch.getCurrentQuantity()).isEqualByComparingTo("100");
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
