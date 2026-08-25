package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterStockEntryCommand;
import com.ceudelavanda.lavandaflow.inventory.domain.*;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.BatchNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterStockEntryTest {

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    private Clock clock;
    private RegisterStockEntry registerStockEntry;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(
            Instant.parse("2026-08-25T16:00:00Z"),
            ZoneOffset.UTC
        );

        registerStockEntry = new RegisterStockEntry(
            batchRepository,
            stockMovementRepository,
            clock
        );
    }

    @Test
    void shouldRegisterStockEntry() {
        var batchId = UUID.randomUUID();

        var batch = new Batch(
            batchId,
            UUID.randomUUID(),
            null,
            "LOT-001",
            new BigDecimal("100"),
            new BigDecimal("100"),
            LocalDate.of(2026, 8, 25),
            null
        );

        when(batchRepository.findById(batchId))
            .thenReturn(Optional.of(batch));

        var command = new RegisterStockEntryCommand(
            batchId,
            new BigDecimal("50"),
            "Supplier replenishment"
        );

        var result = registerStockEntry.execute(command);

        assertThat(result.batchId()).isEqualTo(batchId);
        assertThat(result.type()).isEqualTo(MovementType.ENTRY);
        assertThat(result.quantity()).isEqualByComparingTo("50");
        assertThat(result.resultingBalance()).isEqualByComparingTo("150");
        assertThat(result.reason()).isEqualTo("Supplier replenishment");
        assertThat(result.occurredAt())
            .isEqualTo(Instant.parse("2026-08-25T16:00:00Z"));
        verify(batchRepository).findById(batchId);
        verify(batchRepository).save(batch);
        verify(stockMovementRepository).save(any(StockMovement.class));
    }

    @Test
    void shouldThrowWhenBatchDoesNotExist() {
        var batchId = UUID.randomUUID();

        when(batchRepository.findById(batchId))
            .thenReturn(Optional.empty());

        var command = new RegisterStockEntryCommand(
            batchId,
            new BigDecimal("50"),
            null
        );

        assertThatThrownBy(() -> registerStockEntry.execute(command))
            .isInstanceOf(BatchNotFoundException.class)
            .satisfies(exception -> {
                var batchNotFound = (BatchNotFoundException) exception;
                assertThat(batchNotFound.getBatchId()).isEqualTo(batchId);
            });

        verify(batchRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void shouldRejectZeroQuantity() {
        var batchId = UUID.randomUUID();

        var batch = Batch.create(
            UUID.randomUUID(),
            null,
            "LOT-001",
            new BigDecimal("100"),
            LocalDate.of(2026, 8, 25),
            null
        );

        when(batchRepository.findById(batchId))
            .thenReturn(Optional.of(batch));

        var command = new RegisterStockEntryCommand(
            batchId,
            BigDecimal.ZERO,
            null
        );

        assertThatThrownBy(() -> registerStockEntry.execute(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("quantity must be greater than zero");

        verify(batchRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

}
