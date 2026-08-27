package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterFefoWithdrawalCommand;
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
class RegisterFefoWithdrawalIntegrationTest {

    @Autowired
    private RegisterFefoWithdrawal registerFefoWithdrawal;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private BatchRepository batchRepository;

    @MockitoBean
    private StockMovementRepository stockMovementRepository;

    @Test
    void shouldRollbackAllBatchBalancesWhenMovementPersistenceFailsDuringMultiBatchWithdrawal() {
        var inventoryItem = inventoryItemRepository.save(InventoryItem.create(
            "FEFO rollback essence",
            null,
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        ));
        var firstBatch = batchRepository.save(Batch.create(
            inventoryItem.getId(),
            null,
            "FEFO-ROLLBACK-1",
            new BigDecimal("15.000"),
            LocalDate.of(2026, 8, 20),
            LocalDate.of(2026, 9, 1)
        ));
        var secondBatch = batchRepository.save(Batch.create(
            inventoryItem.getId(),
            null,
            "FEFO-ROLLBACK-2",
            new BigDecimal("25.000"),
            LocalDate.of(2026, 8, 21),
            LocalDate.of(2026, 9, 2)
        ));

        when(stockMovementRepository.save(any(StockMovement.class)))
            .thenAnswer(invocation -> invocation.getArgument(0))
            .thenThrow(new RuntimeException("Second movement persistence failed"));

        assertThatThrownBy(() -> registerFefoWithdrawal.execute(
            new RegisterFefoWithdrawalCommand(
                inventoryItem.getId(),
                new BigDecimal("40.000"),
                "Inventory use"
            )
        ))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Second movement persistence failed");

        assertThat(batchRepository.findById(firstBatch.getId()))
            .isPresent()
            .get()
            .satisfies(batch -> assertThat(batch.getCurrentQuantity()).isEqualByComparingTo("15.000"));
        assertThat(batchRepository.findById(secondBatch.getId()))
            .isPresent()
            .get()
            .satisfies(batch -> assertThat(batch.getCurrentQuantity()).isEqualByComparingTo("25.000"));
    }
}
