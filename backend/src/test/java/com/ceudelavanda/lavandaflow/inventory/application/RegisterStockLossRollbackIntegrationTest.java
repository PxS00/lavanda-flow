package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.inventory.application.movement.RegisterStockLoss;
import com.ceudelavanda.lavandaflow.inventory.application.movement.RegisterStockLossCommand;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RegisterStockLossRollbackIntegrationTest {

    @Autowired
    private RegisterStockLoss registerStockLoss;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private BatchRepository batchRepository;

    @MockitoBean
    private StockMovementRepository stockMovementRepository;

    @Test
    void shouldRollbackPostgresBatchBalanceWhenMovementPersistenceFails() {
        var item = inventoryItemRepository.save(InventoryItem.create(
            "Rollback item " + UUID.randomUUID(), "Test item", Category.OTHER, UnitOfMeasure.UNIT
        ));
        var batch = batchRepository.save(Batch.create(
            item.getId(), null, "LOT-" + UUID.randomUUID(), new BigDecimal("100.000000"),
            LocalDate.of(2026, 8, 1), null
        ));
        when(stockMovementRepository.save(any(StockMovement.class)))
            .thenThrow(new RuntimeException("Movement persistence failed"));

        assertThatThrownBy(() -> registerStockLoss.execute(new RegisterStockLossCommand(
            batch.getId(), new BigDecimal("50.000000"), "Damaged"
        ))).isInstanceOf(RuntimeException.class).hasMessage("Movement persistence failed");

        assertThat(batchRepository.findById(batch.getId())).get()
            .satisfies(found -> assertThat(found.getCurrentQuantity()).isEqualByComparingTo("100.000000"));
    }
}
