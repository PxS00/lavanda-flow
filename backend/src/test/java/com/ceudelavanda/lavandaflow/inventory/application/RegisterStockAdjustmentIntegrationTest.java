package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.catalog.domain.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterStockAdjustmentCommand;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RegisterStockAdjustmentIntegrationTest {

    @Autowired
    private RegisterStockAdjustment registerStockAdjustment;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private BatchRepository batchRepository;

    @MockitoBean
    private StockMovementRepository stockMovementRepository;

    @Test
    void shouldRollbackBatchBalanceWhenNegativeAdjustmentMovementPersistenceFails() {
        var inventoryItem = inventoryItemRepository.save(
            InventoryItem.create(
                "Good Girl Essence",
                "Fragrance essence",
                Category.ESSENCE,
                UnitOfMeasure.MILLILITER
            )
        );

        var batch = Batch.create(
            inventoryItem.getId(),
            null,
            "GG-2026-01",
            new BigDecimal("100.000"),
            LocalDate.of(2026, 8, 25),
            null
        );

        batchRepository.save(batch);

        when(stockMovementRepository.save(any(StockMovement.class)))
            .thenThrow(new RuntimeException("Movement persistence failed"));

        var command = new RegisterStockAdjustmentCommand(
            batch.getId(),
            new BigDecimal("-50.000"),
            "Physical count correction"
        );

        assertThatThrownBy(() -> registerStockAdjustment.execute(command))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Movement persistence failed");

        var persistedBatch = batchRepository.findById(batch.getId());

        assertThat(persistedBatch)
            .isPresent()
            .get()
            .satisfies(found ->
                assertThat(found.getCurrentQuantity())
                    .isEqualByComparingTo("100.000")
            );
    }
}
