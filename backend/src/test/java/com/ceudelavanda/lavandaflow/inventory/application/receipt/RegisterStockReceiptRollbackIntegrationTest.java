package com.ceudelavanda.lavandaflow.inventory.application.receipt;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.application.RegisterInventoryItem;
import com.ceudelavanda.lavandaflow.catalog.application.RegisterInventoryItemCommand;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovement;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RegisterStockReceiptRollbackIntegrationTest {

    @Autowired private RegisterInventoryItem registerInventoryItem;
    @Autowired private RegisterStockReceipt registerStockReceipt;
    @Autowired private BatchRepository batchRepository;

    @MockitoBean private StockMovementRepository stockMovementRepository;

    @Test
    void shouldRollbackNewBatchWhenInitialMovementPersistenceFails() {
        var item = registerInventoryItem.execute(new RegisterInventoryItemCommand(
            "Receipt-96 rollback essence",
            null,
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        ));
        when(stockMovementRepository.save(any(StockMovement.class)))
            .thenThrow(new RuntimeException("movement persistence failed"));

        assertThatThrownBy(() -> registerStockReceipt.execute(new RegisterStockReceiptCommand(
            item.id(),
            null,
            "LOT-96-ROLLBACK",
            new BigDecimal("10.000000"),
            LocalDate.of(2026, 8, 31),
            null,
            null
        )))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("movement persistence failed");

        assertThat(batchRepository.findByInventoryItemId(item.id())).isEmpty();
    }
}
