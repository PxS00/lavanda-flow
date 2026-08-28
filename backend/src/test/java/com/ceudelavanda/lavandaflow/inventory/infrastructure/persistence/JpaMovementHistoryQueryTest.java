package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.inventory.application.history.GetMovementHistoryQuery;
import com.ceudelavanda.lavandaflow.inventory.application.history.MovementHistoryQuery;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovement;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JpaMovementHistoryQueryTest {

    @Autowired
    private MovementHistoryQuery movementHistoryQuery;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Test
    void shouldFilterMovementHistoryByInventoryItemAcrossBatches() {
        var firstItem = createItem("Lavender Essence");
        var secondItem = createItem("Cereal Alcohol");
        var firstBatch = createBatch(firstItem.getId(), "LAV-01");
        var secondBatch = createBatch(firstItem.getId(), "LAV-02");
        var otherBatch = createBatch(secondItem.getId(), "ALC-01");
        var older = movement(
            "00000000-0000-0000-0000-000000000001",
            firstBatch.getId(),
            MovementType.ENTRY,
            "100",
            "2026-08-27T10:00:00Z"
        );
        var newer = movement(
            "00000000-0000-0000-0000-000000000002",
            secondBatch.getId(),
            MovementType.CONSUMPTION,
            "10",
            "2026-08-27T12:00:00Z"
        );
        var unrelated = movement(
            "00000000-0000-0000-0000-000000000003",
            otherBatch.getId(),
            MovementType.ENTRY,
            "50",
            "2026-08-27T11:00:00Z"
        );
        stockMovementRepository.save(older);
        stockMovementRepository.save(newer);
        stockMovementRepository.save(unrelated);

        var result = movementHistoryQuery.find(new GetMovementHistoryQuery(
            firstItem.getId(),
            null,
            null,
            null,
            null,
            0,
            20
        ));

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content())
            .extracting(entry -> entry.movementId())
            .containsExactly(newer.id(), older.id());
        assertThat(result.content())
            .extracting(entry -> entry.inventoryItemId())
            .containsOnly(firstItem.getId());
        assertThat(result.content())
            .extracting(entry -> entry.lotCode())
            .containsExactly("LAV-02", "LAV-01");
    }

    @Test
    void shouldCombineBatchTypeAndHalfOpenDateRangeFilters() {
        var item = createItem("Good Girl Essence");
        var batch = createBatch(item.getId(), "GG-01");
        var atLowerBound = movement(
            "00000000-0000-0000-0000-000000000011",
            batch.getId(),
            MovementType.CONSUMPTION,
            "5",
            "2026-08-27T11:00:00Z"
        );
        var atUpperBound = movement(
            "00000000-0000-0000-0000-000000000012",
            batch.getId(),
            MovementType.CONSUMPTION,
            "6",
            "2026-08-27T12:00:00Z"
        );
        var wrongType = movement(
            "00000000-0000-0000-0000-000000000013",
            batch.getId(),
            MovementType.ADJUSTMENT_IN,
            "7",
            "2026-08-27T11:30:00Z"
        );
        stockMovementRepository.save(atLowerBound);
        stockMovementRepository.save(atUpperBound);
        stockMovementRepository.save(wrongType);

        var result = movementHistoryQuery.find(new GetMovementHistoryQuery(
            null,
            batch.getId(),
            MovementType.CONSUMPTION,
            Instant.parse("2026-08-27T11:00:00Z"),
            Instant.parse("2026-08-27T12:00:00Z"),
            0,
            20
        ));

        assertThat(result.content())
            .extracting(entry -> entry.movementId())
            .containsExactly(atLowerBound.id());
    }

    @Test
    void shouldPaginateWithDeterministicMovementIdTieBreaker() {
        var item = createItem("Fixative");
        var batch = createBatch(item.getId(), "FIX-01");
        var occurredAt = "2026-08-27T15:00:00Z";
        var first = movement(
            "00000000-0000-0000-0000-000000000021",
            batch.getId(),
            MovementType.ENTRY,
            "1",
            occurredAt
        );
        var second = movement(
            "00000000-0000-0000-0000-000000000022",
            batch.getId(),
            MovementType.ENTRY,
            "1",
            occurredAt
        );
        var third = movement(
            "00000000-0000-0000-0000-000000000023",
            batch.getId(),
            MovementType.ENTRY,
            "1",
            occurredAt
        );
        stockMovementRepository.save(first);
        stockMovementRepository.save(second);
        stockMovementRepository.save(third);

        var firstPage = movementHistoryQuery.find(new GetMovementHistoryQuery(
            null,
            batch.getId(),
            null,
            null,
            null,
            0,
            2
        ));
        var secondPage = movementHistoryQuery.find(new GetMovementHistoryQuery(
            null,
            batch.getId(),
            null,
            null,
            null,
            1,
            2
        ));

        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.content())
            .extracting(entry -> entry.movementId())
            .containsExactly(third.id(), second.id());
        assertThat(secondPage.content())
            .extracting(entry -> entry.movementId())
            .containsExactly(first.id());
    }

    private InventoryItem createItem(String name) {
        return inventoryItemRepository.save(InventoryItem.create(
            name,
            null,
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        ));
    }

    private Batch createBatch(UUID inventoryItemId, String lotCode) {
        return batchRepository.save(Batch.create(
            inventoryItemId,
            null,
            lotCode,
            new BigDecimal("100.000000"),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2027, 8, 1)
        ));
    }

    private static StockMovement movement(
        String movementId,
        UUID batchId,
        MovementType type,
        String quantity,
        String occurredAt
    ) {
        return new StockMovement(
            UUID.fromString(movementId),
            batchId,
            type,
            new BigDecimal(quantity),
            null,
            Instant.parse(occurredAt)
        );
    }
}
