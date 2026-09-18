package com.ceudelavanda.lavandaflow.inventory.application.receipt;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.application.RegisterInventoryItem;
import com.ceudelavanda.lavandaflow.catalog.application.RegisterInventoryItemCommand;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.inventory.application.batch.GetBatchInventory;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InactiveSupplierException;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import com.ceudelavanda.lavandaflow.suppliers.application.RegisterSupplier;
import com.ceudelavanda.lavandaflow.suppliers.application.RegisterSupplierCommand;
import com.ceudelavanda.lavandaflow.suppliers.application.UpdateSupplier;
import com.ceudelavanda.lavandaflow.suppliers.application.UpdateSupplierCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RegisterStockReceiptIntegrationTest {

    @Autowired private RegisterInventoryItem registerInventoryItem;
    @Autowired private RegisterSupplier registerSupplier;
    @Autowired private UpdateSupplier updateSupplier;
    @Autowired private RegisterStockReceipt registerStockReceipt;
    @Autowired private BatchRepository batchRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private GetBatchInventory getBatchInventory;

    @Test
    void shouldPersistBatchInitialEntryAndExposeReceiptThroughBatchQuery() {
        var item = registerInventoryItem.execute(new RegisterInventoryItemCommand(
            "Receipt-96 essence",
            "Stock receipt integration fixture",
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        ));
        var supplier = registerSupplier.execute(new RegisterSupplierCommand(
            "Receipt-96 supplier",
            "SUP-96",
            "supplier@example.test",
            null
        ));
        var quantity = new BigDecimal("1234567890123.123456");

        var result = registerStockReceipt.execute(new RegisterStockReceiptCommand(
            item.id(),
            supplier.id(),
            "  LOT-96-INTEGRATION  ",
            quantity,
            LocalDate.of(2026, 8, 31),
            LocalDate.of(2027, 8, 31),
            "  Initial purchase receipt  "
        ));

        var batch = batchRepository.findById(result.batchId()).orElseThrow();
        var movements = stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(result.batchId());
        var operationalBatches = getBatchInventory.execute(item.id()).batches();

        assertThat(batch.getInventoryItemId()).isEqualTo(item.id());
        assertThat(batch.getSupplierId()).isEqualTo(supplier.id());
        assertThat(batch.getLotCode()).isEqualTo("LOT-96-INTEGRATION");
        assertThat(batch.getInitialQuantity()).isEqualByComparingTo(quantity);
        assertThat(batch.getCurrentQuantity()).isEqualByComparingTo(quantity);

        assertThat(movements).hasSize(1);
        assertThat(movements.getFirst().id()).isEqualTo(result.movementId());
        assertThat(movements.getFirst().type()).isEqualTo(MovementType.ENTRY);
        assertThat(movements.getFirst().quantity()).isEqualByComparingTo(quantity);
        assertThat(movements.getFirst().reason()).isEqualTo("Initial purchase receipt");

        assertThat(operationalBatches)
            .anySatisfy(entry -> {
                assertThat(entry.batchId()).isEqualTo(result.batchId());
                assertThat(entry.currentQuantity()).isEqualByComparingTo(quantity);
                assertThat(entry.supplierId()).isEqualTo(supplier.id());
                assertThat(entry.lotCode()).isEqualTo("LOT-96-INTEGRATION");
            });
    }

    @Test
    void shouldApplySupplierMaintenanceToNewReceiptsWithoutChangingExistingBatchOrigin() {
        var item = registerInventoryItem.execute(new RegisterInventoryItemCommand(
            "Receipt-223 essence",
            "Supplier maintenance integration fixture",
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        ));
        var supplier = registerSupplier.execute(new RegisterSupplierCommand(
            "Receipt-223 supplier",
            "SUP-223",
            "supplier@example.test",
            null
        ));
        var today = LocalDate.now();
        var firstReceipt = registerStockReceipt.execute(new RegisterStockReceiptCommand(
            item.id(),
            supplier.id(),
            "LOT-223-ORIGINAL",
            new BigDecimal("10.000000"),
            today,
            today.plusYears(1),
            "Initial receipt"
        ));

        updateSupplier.execute(new UpdateSupplierCommand(
            supplier.id(), supplier.name(), supplier.identifier(), supplier.contact(), supplier.notes(), false
        ));

        assertThatThrownBy(() -> registerStockReceipt.execute(new RegisterStockReceiptCommand(
            item.id(),
            supplier.id(),
            "LOT-223-INACTIVE",
            new BigDecimal("10.000000"),
            today,
            today.plusYears(1),
            "Rejected receipt"
        ))).isInstanceOf(InactiveSupplierException.class);
        assertThat(batchRepository.findById(firstReceipt.batchId()).orElseThrow().getSupplierId())
            .isEqualTo(supplier.id());

        updateSupplier.execute(new UpdateSupplierCommand(
            supplier.id(), supplier.name(), supplier.identifier(), supplier.contact(), supplier.notes(), true
        ));

        var reactivatedReceipt = registerStockReceipt.execute(new RegisterStockReceiptCommand(
            item.id(),
            supplier.id(),
            "LOT-223-REACTIVATED",
            new BigDecimal("10.000000"),
            today,
            today.plusYears(1),
            "Reactivated receipt"
        ));

        assertThat(reactivatedReceipt.supplierId()).isEqualTo(supplier.id());
    }
}
