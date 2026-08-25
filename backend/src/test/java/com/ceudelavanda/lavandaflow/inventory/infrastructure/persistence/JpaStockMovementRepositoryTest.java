package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.catalog.domain.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JpaStockMovementRepositoryTest {

    @Autowired
    private StockMovementRepository movementRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Test
    void shouldSaveAndFindBatchHistoryInChronologicalOrder() {
        var batch = createBatch();
        var laterMovement = StockMovement.create(
            batch.getId(),
            MovementType.CONSUMPTION,
            new BigDecimal("50.000"),
            "Used in production",
            Instant.parse("2026-08-24T14:00:00Z")
        );
        var earlierMovement = StockMovement.create(
            batch.getId(),
            MovementType.ENTRY,
            new BigDecimal("100.000"),
            "Initial receipt",
            Instant.parse("2026-08-24T10:00:00Z")
        );

        movementRepository.save(laterMovement);
        movementRepository.save(earlierMovement);

        var history = movementRepository.findByBatchIdOrderByOccurredAtAsc(batch.getId());

        assertThat(history)
            .extracting(StockMovement::id)
            .containsExactly(earlierMovement.id(), laterMovement.id());
    }

    private Batch createBatch() {
        var item = inventoryItemRepository.save(InventoryItem.create(
            "Good Girl Essence",
            null,
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        ));

        return batchRepository.save(Batch.create(
            item.getId(),
            null,
            "GG-2026-01",
            new BigDecimal("100.000"),
            LocalDate.of(2026, 8, 24),
            null
        ));
    }
}
