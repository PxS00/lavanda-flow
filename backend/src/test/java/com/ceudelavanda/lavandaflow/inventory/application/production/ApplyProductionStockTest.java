package com.ceudelavanda.lavandaflow.inventory.application.production;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemOperationLock;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemSnapshot;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.*;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovement;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.*;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplyProductionStockTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-03T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    @Mock private InventoryItemOperationLock inventoryItemOperationLock;
    @Mock private BatchRepository batchRepository;
    @Mock private StockMovementRepository stockMovementRepository;

    private ApplyProductionStock applyProductionStock;

    @BeforeEach
    void setUp() {
        applyProductionStock = new ApplyProductionStock(
            inventoryItemOperationLock, batchRepository, stockMovementRepository, CLOCK
        );
    }

    @Test
    void shouldApplyOneSourceAndCreateOneOrdinaryOutputBatch() {
        var sourceItemId = UUID.randomUUID();
        var outputItemId = UUID.randomUUID();
        var source = batch(sourceItemId, "10", null);
        available(source, sourceItemId, outputItemId);

        var result = applyProductionStock.apply(command(List.of(new ProductionSourceAllocation(source.getId(), new BigDecimal("4"))), outputItemId, "3"));

        assertThat(source.getCurrentQuantity()).isEqualByComparingTo("6");
        var movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(2)).save(movementCaptor.capture());
        assertThat(movementCaptor.getAllValues()).extracting(StockMovement::type)
            .containsExactly(MovementType.CONSUMPTION, MovementType.ENTRY);
        assertThat(result.sourceConsumptions()).singleElement()
            .satisfies(consumption -> assertThat(consumption.quantity()).isEqualByComparingTo("4"));
        assertThatThrownBy(() -> result.sourceConsumptions().add(null)).isInstanceOf(UnsupportedOperationException.class);
        var batchCaptor = ArgumentCaptor.forClass(Batch.class);
        verify(batchRepository, times(2)).save(batchCaptor.capture());
        assertThat(batchCaptor.getAllValues().get(1).getSupplierId()).isNull();
        assertThat(batchCaptor.getAllValues().get(1).getInitialQuantity()).isEqualByComparingTo("3");
    }

    @Test
    void shouldApplyMultipleExactSourceAllocations() {
        var itemId = UUID.randomUUID();
        var outputItemId = UUID.randomUUID();
        var first = batch(itemId, "10", null);
        var second = batch(itemId, "8", null);
        available(first, itemId, outputItemId);
        available(second, itemId, outputItemId);

        var result = applyProductionStock.apply(command(List.of(
            new ProductionSourceAllocation(second.getId(), new BigDecimal("3")),
            new ProductionSourceAllocation(first.getId(), new BigDecimal("4"))
        ), outputItemId, "2"));

        assertThat(first.getCurrentQuantity()).isEqualByComparingTo("6");
        assertThat(second.getCurrentQuantity()).isEqualByComparingTo("5");
        assertThat(result.sourceConsumptions()).hasSize(2);
        verify(stockMovementRepository, times(3)).save(any());
    }

    @Test
    void shouldRejectInvalidPublicCommandBeforeLocking() {
        var outputItemId = UUID.randomUUID();
        assertThatThrownBy(() -> applyProductionStock.apply(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> applyProductionStock.apply(new ProductionStockCommand(
            null, output(outputItemId, "1")
        ))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> applyProductionStock.apply(new ProductionStockCommand(
            List.of(new ProductionSourceAllocation(UUID.randomUUID(), BigDecimal.ONE)), null
        ))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> applyProductionStock.apply(command(List.of(), outputItemId, "1")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> applyProductionStock.apply(new ProductionStockCommand(List.of(
            new ProductionSourceAllocation(null, BigDecimal.ONE)
        ), output(outputItemId, "1")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> applyProductionStock.apply(new ProductionStockCommand(List.of(
            new ProductionSourceAllocation(UUID.randomUUID(), BigDecimal.ONE)
        ), output(null, "1")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> applyProductionStock.apply(new ProductionStockCommand(List.of(
            new ProductionSourceAllocation(UUID.randomUUID(), BigDecimal.ONE)
        ), new ProductionOutputBatch(outputItemId, "LOT", BigDecimal.ONE, null, null))))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> applyProductionStock.apply(new ProductionStockCommand(List.of(
            new ProductionSourceAllocation(UUID.randomUUID(), null)
        ), output(outputItemId, "1")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> applyProductionStock.apply(command(List.of(
            new ProductionSourceAllocation(UUID.randomUUID(), BigDecimal.ZERO)
        ), outputItemId, "1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> applyProductionStock.apply(command(List.of(
            new ProductionSourceAllocation(UUID.randomUUID(), BigDecimal.ONE)
        ), outputItemId, "0"))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(inventoryItemOperationLock, batchRepository, stockMovementRepository);
    }

    @Test
    void shouldDefensivelyCopySourceAllocations() {
        var source = new ProductionSourceAllocation(UUID.randomUUID(), BigDecimal.ONE);
        var sourceAllocations = new ArrayList<>(List.of(source));
        var command = new ProductionStockCommand(sourceAllocations, output(UUID.randomUUID(), "1"));

        sourceAllocations.clear();

        assertThat(command.sourceAllocations()).containsExactly(source);
        assertThatThrownBy(() -> command.sourceAllocations().add(source))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectDuplicateSourceBatchIds() {
        var sourceId = UUID.randomUUID();
        assertThatThrownBy(() -> applyProductionStock.apply(command(List.of(
            new ProductionSourceAllocation(sourceId, BigDecimal.ONE),
            new ProductionSourceAllocation(sourceId, BigDecimal.ONE)
        ), UUID.randomUUID(), "1"))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(batchRepository);
    }

    @Test
    void shouldRejectMissingAndInsufficientSourcesWithoutPersistence() {
        var missingId = UUID.randomUUID();
        assertThatThrownBy(() -> applyProductionStock.apply(command(List.of(
            new ProductionSourceAllocation(missingId, BigDecimal.ONE)
        ), UUID.randomUUID(), "1"))).isInstanceOf(BatchNotFoundException.class);

        reset(batchRepository);
        var sourceItemId = UUID.randomUUID();
        var outputItemId = UUID.randomUUID();
        var source = batch(sourceItemId, "1", null);
        available(source, sourceItemId, outputItemId);
        assertThatThrownBy(() -> applyProductionStock.apply(command(List.of(
            new ProductionSourceAllocation(source.getId(), new BigDecimal("2"))
        ), outputItemId, "1"))).isInstanceOf(InsufficientStockException.class);
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void shouldApplyExactSourceExpirationSemantics() {
        var itemId = UUID.randomUUID();
        var outputItemId = UUID.randomUUID();
        var expiredToday = batch(itemId, "5", LocalDate.of(2026, 9, 3));
        available(expiredToday, itemId, outputItemId);
        assertThatThrownBy(() -> applyProductionStock.apply(command(List.of(
            new ProductionSourceAllocation(expiredToday.getId(), BigDecimal.ONE)
        ), outputItemId, "1"))).isInstanceOf(ExpiredBatchException.class);

        reset(batchRepository, inventoryItemOperationLock, stockMovementRepository);
        var future = batch(itemId, "5", LocalDate.of(2026, 9, 4));
        available(future, itemId, outputItemId);
        applyProductionStock.apply(command(List.of(new ProductionSourceAllocation(future.getId(), BigDecimal.ONE)), outputItemId, "1"));
        assertThat(future.getCurrentQuantity()).isEqualByComparingTo("4");

        reset(batchRepository, inventoryItemOperationLock, stockMovementRepository);
        var noExpiration = batch(itemId, "5", null);
        available(noExpiration, itemId, outputItemId);
        applyProductionStock.apply(command(List.of(new ProductionSourceAllocation(noExpiration.getId(), BigDecimal.ONE)), outputItemId, "1"));
        assertThat(noExpiration.getCurrentQuantity()).isEqualByComparingTo("4");
    }

    @Test
    void shouldRejectMissingOrInactiveOutputItem() {
        var sourceItemId = UUID.randomUUID();
        var outputItemId = UUID.randomUUID();
        var source = batch(sourceItemId, "5", null);
        when(batchRepository.findInventoryItemIdById(source.getId())).thenReturn(Optional.of(sourceItemId));
        when(inventoryItemOperationLock.lockById(sourceItemId)).thenReturn(Optional.of(snapshot(sourceItemId, true)));
        when(inventoryItemOperationLock.lockById(outputItemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applyProductionStock.apply(command(List.of(
            new ProductionSourceAllocation(source.getId(), BigDecimal.ONE)
        ), outputItemId, "1"))).isInstanceOf(InventoryItemNotFoundException.class);

        reset(inventoryItemOperationLock);
        when(inventoryItemOperationLock.lockById(any())).thenAnswer(invocation -> {
            var itemId = invocation.getArgument(0, UUID.class);
            return Optional.of(snapshot(itemId, !itemId.equals(outputItemId)));
        });
        assertThatThrownBy(() -> applyProductionStock.apply(command(List.of(
            new ProductionSourceAllocation(source.getId(), BigDecimal.ONE)
        ), outputItemId, "1"))).isInstanceOf(InactiveInventoryItemException.class);
    }

    @Test
    void shouldRejectInactiveSourceItem() {
        var sourceItemId = UUID.randomUUID();
        var source = batch(sourceItemId, "5", null);
        var outputItemId = UUID.randomUUID();
        when(batchRepository.findInventoryItemIdById(source.getId())).thenReturn(Optional.of(sourceItemId));
        when(inventoryItemOperationLock.lockById(any())).thenAnswer(invocation -> {
            var itemId = invocation.getArgument(0, UUID.class);
            return Optional.of(snapshot(itemId, !itemId.equals(sourceItemId)));
        });

        assertThatThrownBy(() -> applyProductionStock.apply(command(List.of(
            new ProductionSourceAllocation(source.getId(), BigDecimal.ONE)
        ), outputItemId, "1"))).isInstanceOf(InactiveInventoryItemException.class);
        verifyNoInteractions(stockMovementRepository);
    }

    private void available(Batch source, UUID sourceItemId, UUID outputItemId) {
        when(batchRepository.findInventoryItemIdById(source.getId())).thenReturn(Optional.of(sourceItemId));
        when(batchRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(inventoryItemOperationLock.lockById(sourceItemId)).thenReturn(Optional.of(snapshot(sourceItemId, true)));
        when(inventoryItemOperationLock.lockById(outputItemId)).thenReturn(Optional.of(snapshot(outputItemId, true)));
    }

    private static ProductionStockCommand command(List<ProductionSourceAllocation> sources, UUID outputItemId, String outputQuantity) {
        return new ProductionStockCommand(sources, output(outputItemId, outputQuantity));
    }

    private static ProductionOutputBatch output(UUID outputItemId, String quantity) {
        return new ProductionOutputBatch(
            outputItemId, "BDS-000-001-09-2026", new BigDecimal(quantity), LocalDate.of(2026, 9, 3), null
        );
    }

    private static Batch batch(UUID itemId, String quantity, LocalDate expiresAt) {
        return Batch.create(itemId, null, "SOURCE-" + UUID.randomUUID(), new BigDecimal(quantity), LocalDate.of(2026, 8, 20), expiresAt);
    }

    private static InventoryItemSnapshot snapshot(UUID itemId, boolean active) {
        return new InventoryItemSnapshot(itemId, "Item", UnitOfMeasure.UNIT, active);
    }
}
