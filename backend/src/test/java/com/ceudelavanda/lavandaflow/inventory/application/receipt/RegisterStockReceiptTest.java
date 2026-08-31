package com.ceudelavanda.lavandaflow.inventory.application.receipt;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemOperationLock;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemSnapshot;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovement;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InactiveInventoryItemException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InactiveSupplierException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InventoryItemNotFoundException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.SupplierNotFoundException;
import com.ceudelavanda.lavandaflow.suppliers.SupplierLookup;
import com.ceudelavanda.lavandaflow.suppliers.SupplierSnapshot;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterStockReceiptTest {

    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID SUPPLIER_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-31T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(OCCURRED_AT, ZoneOffset.UTC);

    @Mock private InventoryItemOperationLock inventoryItemOperationLock;
    @Mock private SupplierLookup supplierLookup;
    @Mock private BatchRepository batchRepository;
    @Mock private StockMovementRepository stockMovementRepository;

    private RegisterStockReceipt registerStockReceipt;

    @BeforeEach
    void setUp() {
        registerStockReceipt = new RegisterStockReceipt(
            inventoryItemOperationLock,
            supplierLookup,
            batchRepository,
            stockMovementRepository,
            CLOCK
        );
    }

    @Test
    void shouldCreateBatchAndExactlyOneInitialEntryMovement() {
        when(inventoryItemOperationLock.lockById(ITEM_ID)).thenReturn(Optional.of(activeItem()));
        when(supplierLookup.findById(SUPPLIER_ID)).thenReturn(Optional.of(activeSupplier()));

        var result = registerStockReceipt.execute(command(SUPPLIER_ID));

        var batchCaptor = ArgumentCaptor.forClass(Batch.class);
        var movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(batchRepository).save(batchCaptor.capture());
        verify(stockMovementRepository).save(movementCaptor.capture());

        var batch = batchCaptor.getValue();
        var movement = movementCaptor.getValue();
        assertThat(batch.getId()).isEqualTo(result.batchId());
        assertThat(batch.getInitialQuantity()).isEqualByComparingTo("125.500000");
        assertThat(batch.getCurrentQuantity()).isEqualByComparingTo("125.500000");
        assertThat(movement.batchId()).isEqualTo(batch.getId());
        assertThat(movement.type()).isEqualTo(MovementType.ENTRY);
        assertThat(movement.quantity()).isEqualByComparingTo(batch.getInitialQuantity());
        assertThat(movement.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(result.movementId()).isEqualTo(movement.id());
        assertThat(result.reason()).isEqualTo("Purchase receipt");
    }

    @Test
    void shouldAllowReceiptWithoutSupplier() {
        when(inventoryItemOperationLock.lockById(ITEM_ID)).thenReturn(Optional.of(activeItem()));

        var result = registerStockReceipt.execute(command(null));

        assertThat(result.supplierId()).isNull();
        verifyNoInteractions(supplierLookup);
        verify(batchRepository).save(any(Batch.class));
        verify(stockMovementRepository).save(any(StockMovement.class));
    }

    @Test
    void shouldRejectUnknownInventoryItemBeforeAnyPersistence() {
        when(inventoryItemOperationLock.lockById(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registerStockReceipt.execute(command(SUPPLIER_ID)))
            .isInstanceOf(InventoryItemNotFoundException.class);

        verifyNoInteractions(supplierLookup, batchRepository, stockMovementRepository);
    }

    @Test
    void shouldRejectInactiveInventoryItemBeforeAnyPersistence() {
        when(inventoryItemOperationLock.lockById(ITEM_ID)).thenReturn(Optional.of(
            new InventoryItemSnapshot(ITEM_ID, "Lavender", UnitOfMeasure.MILLILITER, false)
        ));

        assertThatThrownBy(() -> registerStockReceipt.execute(command(SUPPLIER_ID)))
            .isInstanceOf(InactiveInventoryItemException.class);

        verifyNoInteractions(supplierLookup, batchRepository, stockMovementRepository);
    }

    @Test
    void shouldRejectUnknownSupplierBeforeAnyPersistence() {
        when(inventoryItemOperationLock.lockById(ITEM_ID)).thenReturn(Optional.of(activeItem()));
        when(supplierLookup.findById(SUPPLIER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registerStockReceipt.execute(command(SUPPLIER_ID)))
            .isInstanceOf(SupplierNotFoundException.class);

        verifyNoInteractions(batchRepository, stockMovementRepository);
    }

    @Test
    void shouldRejectInactiveSupplierBeforeAnyPersistence() {
        when(inventoryItemOperationLock.lockById(ITEM_ID)).thenReturn(Optional.of(activeItem()));
        when(supplierLookup.findById(SUPPLIER_ID)).thenReturn(Optional.of(
            new SupplierSnapshot(SUPPLIER_ID, "Inactive supplier", false)
        ));

        assertThatThrownBy(() -> registerStockReceipt.execute(command(SUPPLIER_ID)))
            .isInstanceOf(InactiveSupplierException.class);

        verifyNoInteractions(batchRepository, stockMovementRepository);
    }

    @Test
    void shouldNotPersistMovementWhenBatchPersistenceFails() {
        when(inventoryItemOperationLock.lockById(ITEM_ID)).thenReturn(Optional.of(activeItem()));
        when(supplierLookup.findById(SUPPLIER_ID)).thenReturn(Optional.of(activeSupplier()));
        doThrow(new RuntimeException("batch persistence failed"))
            .when(batchRepository).save(any(Batch.class));

        assertThatThrownBy(() -> registerStockReceipt.execute(command(SUPPLIER_ID)))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("batch persistence failed");

        verify(stockMovementRepository, never()).save(any(StockMovement.class));
    }

    private static RegisterStockReceiptCommand command(UUID supplierId) {
        return new RegisterStockReceiptCommand(
            ITEM_ID,
            supplierId,
            "  LOT-96  ",
            new BigDecimal("125.500000"),
            LocalDate.of(2026, 8, 31),
            LocalDate.of(2027, 8, 31),
            "  Purchase receipt  "
        );
    }

    private static InventoryItemSnapshot activeItem() {
        return new InventoryItemSnapshot(ITEM_ID, "Lavender", UnitOfMeasure.MILLILITER, true);
    }

    private static SupplierSnapshot activeSupplier() {
        return new SupplierSnapshot(SUPPLIER_ID, "Supplier 96", true);
    }
}
