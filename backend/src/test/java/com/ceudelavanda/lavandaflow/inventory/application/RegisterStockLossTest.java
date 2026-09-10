package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.inventory.application.movement.RegisterStockLoss;
import com.ceudelavanda.lavandaflow.inventory.application.movement.RegisterStockLossCommand;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovement;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InsufficientStockException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InvalidStockMovementReasonException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterStockLossTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-08-25T16:00:00Z");

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    private RegisterStockLoss registerStockLoss;

    @BeforeEach
    void setUp() {
        registerStockLoss = new RegisterStockLoss(
            batchRepository, stockMovementRepository, Clock.fixed(OCCURRED_AT, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldRegisterLossWithPositiveMovementQuantityAndExactZeroBalance() {
        var batchId = UUID.randomUUID();
        var batch = batch(batchId, "12.500000", LocalDate.of(2026, 8, 25));
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        var result = registerStockLoss.execute(new RegisterStockLossCommand(
            batchId, new BigDecimal("12.500000"), "Bottle broke during handling"
        ));

        var movement = captureMovement();
        assertThat(movement.type()).isEqualTo(MovementType.LOSS);
        assertThat(movement.quantity()).isEqualByComparingTo("12.500000");
        assertThat(movement.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(result.type()).isEqualTo(MovementType.LOSS);
        assertThat(result.quantity()).isEqualByComparingTo("12.500000");
        assertThat(result.resultingBalance()).isEqualByComparingTo("0");
        assertThat(batch.getCurrentQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void shouldLockBeforeLoadingAndMutatingSelectedBatch() {
        var batchId = UUID.randomUUID();
        var batch = batch(batchId, "10", null);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        registerStockLoss.execute(new RegisterStockLossCommand(batchId, BigDecimal.ONE, "Damaged"));

        InOrder order = inOrder(batchRepository, stockMovementRepository);
        order.verify(batchRepository).lockByIdForUpdate(batchId);
        order.verify(batchRepository).findById(batchId);
        order.verify(batchRepository).save(batch);
        order.verify(stockMovementRepository).save(any(StockMovement.class));
    }

    @Test
    void shouldRejectInsufficientStockWithoutPersisting() {
        var batchId = UUID.randomUUID();
        var batch = batch(batchId, "10", null);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> registerStockLoss.execute(new RegisterStockLossCommand(
            batchId, new BigDecimal("10.000001"), "Damaged"
        ))).isInstanceOf(InsufficientStockException.class);

        assertThat(batch.getCurrentQuantity()).isEqualByComparingTo("10");
        verify(batchRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void shouldRejectNonPositiveOrInexactQuantityWithoutPersisting() {
        var batchId = UUID.randomUUID();
        var batch = batch(batchId, "10", null);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> registerStockLoss.execute(new RegisterStockLossCommand(batchId, BigDecimal.ZERO, "Damaged")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registerStockLoss.execute(new RegisterStockLossCommand(batchId, new BigDecimal("1.0000001"), "Damaged")))
            .isInstanceOf(IllegalArgumentException.class);

        verify(batchRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void shouldRejectBlankReasonBeforeLockingOrPersisting() {
        assertThatThrownBy(() -> registerStockLoss.execute(new RegisterStockLossCommand(
            UUID.randomUUID(), BigDecimal.ONE, "  "
        ))).isInstanceOf(InvalidStockMovementReasonException.class);

        verify(batchRepository, never()).lockByIdForUpdate(any());
        verify(batchRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    private StockMovement captureMovement() {
        var captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());
        return captor.getValue();
    }

    private static Batch batch(UUID batchId, String balance, LocalDate expiresAt) {
        return new Batch(
            batchId, UUID.randomUUID(), null, "LOT-001", new BigDecimal(balance), new BigDecimal(balance),
            LocalDate.of(2026, 8, 1), expiresAt
        );
    }
}
