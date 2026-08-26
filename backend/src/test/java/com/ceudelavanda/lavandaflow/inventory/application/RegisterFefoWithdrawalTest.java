package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemLookup;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemSnapshot;
import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterFefoWithdrawalCommand;
import com.ceudelavanda.lavandaflow.inventory.application.result.FefoWithdrawalAllocationResult;
import com.ceudelavanda.lavandaflow.inventory.domain.*;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InactiveInventoryItemException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InsufficientEligibleStockException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InventoryItemNotFoundException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterFefoWithdrawalTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-25T16:00:00Z");

    @Mock
    private InventoryItemLookup inventoryItemLookup;

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    private RegisterFefoWithdrawal registerFefoWithdrawal;

    @BeforeEach
    void setUp() {
        registerFefoWithdrawal = useCaseAt(OCCURRED_AT);
    }

    @Test
    void shouldRejectMissingInventoryItem() {
        var itemId = UUID.randomUUID();
        when(inventoryItemLookup.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registerFefoWithdrawal.execute(command(itemId, "1", null)))
            .isInstanceOf(InventoryItemNotFoundException.class)
            .satisfies(exception -> {
                var notFound = (InventoryItemNotFoundException) exception;
                assertThat(notFound.getCode()).isEqualTo("INVENTORY_ITEM_NOT_FOUND");
                assertThat(notFound.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            });

        verify(batchRepository, never()).findByInventoryItemId(any());
        verify(batchRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void shouldRejectInactiveInventoryItem() {
        var itemId = UUID.randomUUID();
        activeItem(itemId, false);

        assertThatThrownBy(() -> registerFefoWithdrawal.execute(command(itemId, "1", null)))
            .isInstanceOf(InactiveInventoryItemException.class)
            .satisfies(exception -> {
                var inactive = (InactiveInventoryItemException) exception;
                assertThat(inactive.getCode()).isEqualTo("INACTIVE_INVENTORY_ITEM");
                assertThat(inactive.getErrorType()).isEqualTo(ErrorType.BUSINESS_RULE);
            });

        verify(batchRepository, never()).findByInventoryItemId(any());
        verify(batchRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void shouldAllocateAndPersistSingleBatchWithdrawal() {
        var itemId = UUID.randomUUID();
        var batch = batch(itemId, "00000000-0000-0000-0000-000000000001", "100.000", "2026-09-01");
        activeItem(itemId, true);
        when(batchRepository.findByInventoryItemId(itemId)).thenReturn(List.of(batch));

        var result = registerFefoWithdrawal.execute(command(itemId, "40.000", "Inventory use"));

        var movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());
        var movement = movementCaptor.getValue();
        assertThat(batch.getCurrentQuantity()).isEqualByComparingTo("60.000");
        assertThat(movement.type()).isEqualTo(MovementType.CONSUMPTION);
        assertThat(movement.quantity()).isEqualByComparingTo("40.000");
        assertThat(movement.reason()).isEqualTo("Inventory use");
        assertThat(movement.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(result.inventoryItemId()).isEqualTo(itemId);
        assertThat(result.requestedQuantity()).isEqualByComparingTo("40.000");
        assertThat(result.allocatedQuantity()).isEqualByComparingTo("40.000");
        assertThat(result.allocations()).singleElement().satisfies(allocation -> {
            assertThat(allocation.batchId()).isEqualTo(batch.getId());
            assertThat(allocation.movementId()).isEqualTo(movement.id());
            assertThat(allocation.quantity()).isEqualByComparingTo("40.000");
        });
        verify(batchRepository).save(batch);
    }

    @Test
    void shouldAllocateAndPersistMultipleBatchWithdrawal() {
        var itemId = UUID.randomUUID();
        var latest = batch(itemId, "00000000-0000-0000-0000-000000000003", "100.000", "2026-09-03");
        var middle = batch(itemId, "00000000-0000-0000-0000-000000000002", "25.000", "2026-09-02");
        var earliest = batch(itemId, "00000000-0000-0000-0000-000000000001", "15.000", "2026-09-01");
        activeItem(itemId, true);
        when(batchRepository.findByInventoryItemId(itemId)).thenReturn(List.of(latest, middle, earliest));

        var result = registerFefoWithdrawal.execute(command(itemId, "80.000", "Production"));

        assertThat(result.allocations()).extracting(FefoWithdrawalAllocationResult::batchId)
            .containsExactly(earliest.getId(), middle.getId(), latest.getId());
        assertThat(result.allocations().get(0).quantity()).isEqualByComparingTo("15.000");
        assertThat(result.allocations().get(1).quantity()).isEqualByComparingTo("25.000");
        assertThat(result.allocations().get(2).quantity()).isEqualByComparingTo("40.000");
        assertThat(result.requestedQuantity()).isEqualByComparingTo(result.allocatedQuantity());
        assertThat(earliest.getCurrentQuantity()).isEqualByComparingTo("0.000");
        assertThat(middle.getCurrentQuantity()).isEqualByComparingTo("0.000");
        assertThat(latest.getCurrentQuantity()).isEqualByComparingTo("60.000");
        verify(batchRepository).save(earliest);
        verify(batchRepository).save(middle);
        verify(batchRepository).save(latest);
        verify(stockMovementRepository, times(3)).save(any(StockMovement.class));
    }

    @Test
    void shouldUseSameOccurredAtForAllMovementsInOneOperation() {
        var itemId = UUID.randomUUID();
        var first = batch(itemId, "00000000-0000-0000-0000-000000000001", "10", "2026-09-01");
        var second = batch(itemId, "00000000-0000-0000-0000-000000000002", "10", "2026-09-02");
        activeItem(itemId, true);
        when(batchRepository.findByInventoryItemId(itemId)).thenReturn(List.of(first, second));

        registerFefoWithdrawal.execute(command(itemId, "20", null));

        var captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(movement ->
            assertThat(movement.occurredAt()).isEqualTo(OCCURRED_AT)
        );
    }

    @Test
    void shouldUseBusinessDateFromConfiguredClock() {
        var itemId = UUID.randomUUID();
        var businessBoundary = Instant.parse("2026-08-26T00:30:00Z");
        registerFefoWithdrawal = useCaseAt(businessBoundary);
        var expiresOnBusinessToday = batch(itemId, "00000000-0000-0000-0000-000000000001", "10", "2026-08-25");
        var future = batch(itemId, "00000000-0000-0000-0000-000000000002", "10", "2026-08-26");
        activeItem(itemId, true);
        when(batchRepository.findByInventoryItemId(itemId)).thenReturn(List.of(expiresOnBusinessToday, future));

        var result = registerFefoWithdrawal.execute(command(itemId, "10", null));

        assertThat(result.allocations()).singleElement()
            .satisfies(allocation -> assertThat(allocation.batchId()).isEqualTo(future.getId()));
        assertThat(expiresOnBusinessToday.getCurrentQuantity()).isEqualByComparingTo("10");
    }

    @Test
    void shouldNotMutateOrPersistAnythingWhenEligibleStockIsInsufficient() {
        var itemId = UUID.randomUUID();
        var eligible = batch(itemId, UUID.randomUUID().toString(), "55.000", "2026-09-01");
        activeItem(itemId, true);
        when(batchRepository.findByInventoryItemId(itemId)).thenReturn(List.of(eligible));

        assertThatThrownBy(() -> registerFefoWithdrawal.execute(command(itemId, "80.000", null)))
            .isInstanceOf(InsufficientEligibleStockException.class);

        assertThat(eligible.getCurrentQuantity()).isEqualByComparingTo("55.000");
        verify(batchRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void shouldNotCountExpiredOrNonExpiringStockTowardAvailability() {
        var itemId = UUID.randomUUID();
        var eligible = batch(itemId, UUID.randomUUID().toString(), "10.000", "2026-09-01");
        var expired = batch(itemId, UUID.randomUUID().toString(), "100.000", "2026-08-24");
        var noExpiry = batch(itemId, UUID.randomUUID().toString(), "100.000", null);
        activeItem(itemId, true);
        when(batchRepository.findByInventoryItemId(itemId)).thenReturn(List.of(eligible, expired, noExpiry));

        assertThatThrownBy(() -> registerFefoWithdrawal.execute(command(itemId, "20.000", null)))
            .isInstanceOf(InsufficientEligibleStockException.class)
            .satisfies(exception -> assertThat(((InsufficientEligibleStockException) exception).getAvailableQuantity())
                .isEqualByComparingTo("10.000"));

        assertThat(eligible.getCurrentQuantity()).isEqualByComparingTo("10.000");
        assertThat(expired.getCurrentQuantity()).isEqualByComparingTo("100.000");
        assertThat(noExpiry.getCurrentQuantity()).isEqualByComparingTo("100.000");
    }

    @Test
    void shouldNormalizeBlankReasonAccordingToExistingStockMovementBehavior() {
        var itemId = UUID.randomUUID();
        var batch = batch(itemId, UUID.randomUUID().toString(), "10", "2026-09-01");
        activeItem(itemId, true);
        when(batchRepository.findByInventoryItemId(itemId)).thenReturn(List.of(batch));

        registerFefoWithdrawal.execute(command(itemId, "1", "   "));

        var captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());
        assertThat(captor.getValue().reason()).isNull();
    }

    @Test
    void shouldPreserveExactBigDecimalQuantitiesAcrossMultipleAllocations() {
        var itemId = UUID.randomUUID();
        var first = batch(itemId, UUID.randomUUID().toString(), "0.100", "2026-09-01");
        var second = batch(itemId, UUID.randomUUID().toString(), "0.200", "2026-09-02");
        activeItem(itemId, true);
        when(batchRepository.findByInventoryItemId(itemId)).thenReturn(List.of(first, second));

        var result = registerFefoWithdrawal.execute(command(itemId, "0.300", null));

        assertThat(result.allocatedQuantity()).isEqualByComparingTo("0.300");
        assertThat(first.getCurrentQuantity()).isEqualByComparingTo("0.000");
        assertThat(second.getCurrentQuantity()).isEqualByComparingTo("0.000");
        var captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).quantity()).isEqualByComparingTo("0.100");
        assertThat(captor.getAllValues().get(1).quantity()).isEqualByComparingTo("0.200");
    }

    private RegisterFefoWithdrawal useCaseAt(Instant instant) {
        return new RegisterFefoWithdrawal(
            inventoryItemLookup,
            batchRepository,
            stockMovementRepository,
            Clock.fixed(instant, BUSINESS_ZONE)
        );
    }

    private void activeItem(UUID itemId, boolean active) {
        when(inventoryItemLookup.findById(itemId))
            .thenReturn(Optional.of(new InventoryItemSnapshot(itemId, active)));
    }

    private static RegisterFefoWithdrawalCommand command(UUID itemId, String quantity, String reason) {
        return new RegisterFefoWithdrawalCommand(itemId, new BigDecimal(quantity), reason);
    }

    private static Batch batch(UUID itemId, String batchId, String quantity, String expiresAt) {
        return new Batch(
            UUID.fromString(batchId), itemId, null, "LOT-" + batchId,
            new BigDecimal(quantity), new BigDecimal(quantity), LocalDate.of(2026, 8, 20),
            expiresAt == null ? null : LocalDate.parse(expiresAt)
        );
    }
}
