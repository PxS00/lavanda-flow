package com.ceudelavanda.lavandaflow.inventory.application.receipt;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.application.RegisterInventoryItem;
import com.ceudelavanda.lavandaflow.catalog.application.RegisterInventoryItemCommand;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.inventory.application.batch.GetBatchInventory;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import com.ceudelavanda.lavandaflow.suppliers.application.RegisterSupplier;
import com.ceudelavanda.lavandaflow.suppliers.application.RegisterSupplierCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RegisterStockReceiptIntegrationTest {

    @Autowired private RegisterInventoryItem registerInventoryItem;
    @Autowired private RegisterSupplier registerSupplier;
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
}
