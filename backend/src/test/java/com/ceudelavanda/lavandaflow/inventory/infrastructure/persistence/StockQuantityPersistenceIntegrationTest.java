package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovement;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StockQuantityPersistenceIntegrationTest {

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Test
    void shouldPersistSmallestSupportedQuantityExactly() {
        var inventoryItem = inventoryItemRepository.save(
            InventoryItem.create(
                "Precision Test Essence",
                "Boundary persistence test",
                Category.ESSENCE,
                UnitOfMeasure.MILLILITER
            )
        );

        var quantity = new BigDecimal("0.000001");
        var batch = Batch.create(
            inventoryItem.getId(),
            null,
            "PRECISION-001",
            quantity,
            LocalDate.of(2026, 8, 27),
            null
        );

        batchRepository.save(batch);
        stockMovementRepository.save(
            StockMovement.create(
                batch.getId(),
                MovementType.ENTRY,
                quantity,
                "Boundary persistence test",
                Instant.parse("2026-08-27T12:00:00Z")
            )
        );

        assertThat(batchRepository.findById(batch.getId()))
            .isPresent()
            .get()
            .satisfies(found -> {
                assertThat(found.getInitialQuantity()).isEqualByComparingTo(quantity);
                assertThat(found.getCurrentQuantity()).isEqualByComparingTo(quantity);
            });

        assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(batch.getId()))
            .singleElement()
            .satisfies(movement ->
                assertThat(movement.quantity()).isEqualByComparingTo(quantity)
            );
    }
}
