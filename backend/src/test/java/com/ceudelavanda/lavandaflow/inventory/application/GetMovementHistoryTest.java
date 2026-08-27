package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemLookup;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemSnapshot;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.application.query.GetMovementHistoryQuery;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InventoryItemNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetMovementHistoryTest {

    @Mock
    private MovementHistoryQuery movementHistoryQuery;

    @Mock
    private InventoryItemLookup inventoryItemLookup;

    private GetMovementHistory getMovementHistory;

    @BeforeEach
    void setUp() {
        getMovementHistory = new GetMovementHistory(movementHistoryQuery, inventoryItemLookup);
    }

    @Test
    void shouldEnrichMovementEntriesThroughCatalogPublicApi() {
        var itemId = UUID.randomUUID();
        var batchId = UUID.randomUUID();
        var movementId = UUID.randomUUID();
        var occurredAt = Instant.parse("2026-08-27T14:30:00Z");
        var query = query();
        var entry = new MovementHistoryEntry(
            movementId,
            itemId,
            batchId,
            "ESS-042",
            MovementType.CONSUMPTION,
            new BigDecimal("25.500000"),
            "Perfume production",
            occurredAt
        );
        when(movementHistoryQuery.find(query))
            .thenReturn(new MovementHistoryPage(List.of(entry), 0, 20, 1, 1));
        when(inventoryItemLookup.findByIds(List.of(itemId)))
            .thenReturn(List.of(new InventoryItemSnapshot(
                itemId,
                "Good Girl Essence",
                UnitOfMeasure.MILLILITER,
                false
            )));

        var result = getMovementHistory.execute(query);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).singleElement().satisfies(movement -> {
            assertThat(movement.movementId()).isEqualTo(movementId);
            assertThat(movement.inventoryItemId()).isEqualTo(itemId);
            assertThat(movement.inventoryItemName()).isEqualTo("Good Girl Essence");
            assertThat(movement.unitOfMeasure()).isEqualTo(UnitOfMeasure.MILLILITER);
            assertThat(movement.inventoryItemActive()).isFalse();
            assertThat(movement.batchId()).isEqualTo(batchId);
            assertThat(movement.lotCode()).isEqualTo("ESS-042");
            assertThat(movement.type()).isEqualTo(MovementType.CONSUMPTION);
            assertThat(movement.quantity()).isEqualByComparingTo("25.500000");
            assertThat(movement.reason()).isEqualTo("Perfume production");
            assertThat(movement.occurredAt()).isEqualTo(occurredAt);
        });
    }

    @Test
    void shouldNotQueryCatalogForEmptyPage() {
        var query = query();
        when(movementHistoryQuery.find(query))
            .thenReturn(new MovementHistoryPage(List.of(), 0, 20, 0, 0));

        var result = getMovementHistory.execute(query);

        assertThat(result.content()).isEmpty();
        verifyNoInteractions(inventoryItemLookup);
    }

    @Test
    void shouldFailWhenPersistedMovementReferencesMissingCatalogItem() {
        var itemId = UUID.randomUUID();
        var query = query();
        var entry = new MovementHistoryEntry(
            UUID.randomUUID(),
            itemId,
            UUID.randomUUID(),
            null,
            MovementType.ENTRY,
            BigDecimal.ONE,
            null,
            Instant.parse("2026-08-27T10:00:00Z")
        );
        when(movementHistoryQuery.find(query))
            .thenReturn(new MovementHistoryPage(List.of(entry), 0, 20, 1, 1));
        when(inventoryItemLookup.findByIds(List.of(itemId))).thenReturn(List.of());

        assertThatThrownBy(() -> getMovementHistory.execute(query))
            .isInstanceOf(InventoryItemNotFoundException.class);
    }

    private static GetMovementHistoryQuery query() {
        return new GetMovementHistoryQuery(null, null, null, null, null, 0, 20);
    }
}
