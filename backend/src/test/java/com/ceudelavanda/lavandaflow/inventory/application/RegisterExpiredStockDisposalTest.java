package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.inventory.application.movement.RegisterExpiredStockDisposal;
import com.ceudelavanda.lavandaflow.inventory.application.movement.RegisterExpiredStockDisposalCommand;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovement;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.BatchNotExpiredException;
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
class RegisterExpiredStockDisposalTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-08-25T16:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 25);

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    private RegisterExpiredStockDisposal registerExpiredStockDisposal;

    @BeforeEach
    void setUp() {
        registerExpiredStockDisposal = new RegisterExpiredStockDisposal(
            batchRepository, stockMovementRepository, Clock.fixed(OCCURRED_AT, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldRegisterDisposalForYesterdayExpiredBatchWithExactZeroBalance() {
        var batchId = UUID.randomUUID();
        var batch = batch(batchId, "12.500000", TODAY.minusDays(1));
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        var result = registerExpiredStockDisposal.execute(new RegisterExpiredStockDisposalCommand(
            batchId, new BigDecimal("12.500000"), "Expired stock physically discarded"
        ));

        var captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(MovementType.EXPIRED_DISPOSAL);
        assertThat(captor.getValue().quantity()).isEqualByComparingTo("12.500000");
        assertThat(captor.getValue().occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(result.type()).isEqualTo(MovementType.EXPIRED_DISPOSAL);
        assertThat(result.resultingBalance()).isEqualByComparingTo("0");
        InOrder order = inOrder(batchRepository, stockMovementRepository);
        order.verify(batchRepository).lockByIdForUpdate(batchId);
        order.verify(batchRepository).findById(batchId);
        order.verify(batchRepository).save(batch);
        order.verify(stockMovementRepository).save(any(StockMovement.class));
    }

    @Test
    void shouldAcceptBatchExpiringToday() {
        var batchId = UUID.randomUUID();
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch(batchId, "10", TODAY)));

        registerExpiredStockDisposal.execute(new RegisterExpiredStockDisposalCommand(batchId, BigDecimal.ONE, "Discarded"));

        verify(stockMovementRepository).save(any(StockMovement.class));
    }

    @Test
    void shouldRejectTomorrowOrNullExpirationWithBatchNotExpiredCode() {
        assertNotExpired(TODAY.plusDays(1));
        assertNotExpired(null);
    }

    @Test
    void shouldRejectInsufficientStockWithoutPersisting() {
        var batchId = UUID.randomUUID();
        var batch = batch(batchId, "10", TODAY);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> registerExpiredStockDisposal.execute(new RegisterExpiredStockDisposalCommand(
            batchId, new BigDecimal("10.000001"), "Discarded"
        ))).isInstanceOf(InsufficientStockException.class);

        assertThat(batch.getCurrentQuantity()).isEqualByComparingTo("10");
        verify(batchRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void shouldRejectNonPositiveOrInexactQuantityWithoutPersisting() {
        var batchId = UUID.randomUUID();
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch(batchId, "10", TODAY)));

        assertThatThrownBy(() -> registerExpiredStockDisposal.execute(new RegisterExpiredStockDisposalCommand(
            batchId, BigDecimal.ZERO, "Discarded"
        ))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registerExpiredStockDisposal.execute(new RegisterExpiredStockDisposalCommand(
            batchId, new BigDecimal("1.0000001"), "Discarded"
        ))).isInstanceOf(IllegalArgumentException.class);

        verify(batchRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void shouldRejectBlankReasonBeforeLockingOrPersisting() {
        assertThatThrownBy(() -> registerExpiredStockDisposal.execute(new RegisterExpiredStockDisposalCommand(
            UUID.randomUUID(), BigDecimal.ONE, " "
        ))).isInstanceOf(InvalidStockMovementReasonException.class);

        verify(batchRepository, never()).lockByIdForUpdate(any());
        verify(stockMovementRepository, never()).save(any());
    }

    private void assertNotExpired(LocalDate expiresAt) {
        var batchId = UUID.randomUUID();
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch(batchId, "10", expiresAt)));

        assertThatThrownBy(() -> registerExpiredStockDisposal.execute(new RegisterExpiredStockDisposalCommand(
            batchId, BigDecimal.ONE, "Discarded"
        ))).isInstanceOf(BatchNotExpiredException.class)
            .satisfies(exception -> assertThat(((BatchNotExpiredException) exception).getCode())
                .isEqualTo("BATCH_NOT_EXPIRED"));

        verify(stockMovementRepository, never()).save(any());
    }

    private static Batch batch(UUID batchId, String balance, LocalDate expiresAt) {
        return new Batch(
            batchId, UUID.randomUUID(), null, "LOT-001", new BigDecimal(balance), new BigDecimal(balance),
            LocalDate.of(2026, 8, 1), expiresAt
        );
    }
}
