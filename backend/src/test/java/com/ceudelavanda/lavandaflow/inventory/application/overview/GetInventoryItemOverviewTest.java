package com.ceudelavanda.lavandaflow.inventory.application.overview;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemDetails;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemDetailsLookup;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InvalidExpirationAlertWindowException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InventoryItemNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetInventoryItemOverviewTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 31);
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-08-31T15:00:00Z"), ZoneId.of("America/Sao_Paulo")
    );

    @Mock private InventoryItemDetailsLookup inventoryItemDetailsLookup;
    @Mock private InventoryItemOverviewQuery inventoryItemOverviewQuery;
    private GetInventoryItemOverview getOverview;

    @BeforeEach
    void setUp() {
        getOverview = new GetInventoryItemOverview(inventoryItemDetailsLookup, inventoryItemOverviewQuery, CLOCK);
    }

    @Test
    void shouldBuildCohesiveOverviewFromCatalogAndInventoryMetrics() {
        var id = UUID.randomUUID();
        when(inventoryItemDetailsLookup.findById(id)).thenReturn(Optional.of(item(id, true)));
        when(inventoryItemOverviewQuery.findMetrics(id, TODAY, TODAY.plusDays(30)))
            .thenReturn(metrics("20", "8", "10", 3, TODAY.plusDays(5), 1, 1));

        var result = getOverview.execute(new GetInventoryItemOverviewQuery(id, 30));

        assertThat(result.inventoryItemId()).isEqualTo(id);
        assertThat(result.name()).isEqualTo("Lavender Essence");
        assertThat(result.category()).isEqualTo("ESSENCE");
        assertThat(result.unitOfMeasure()).isEqualTo(UnitOfMeasure.MILLILITER);
        assertThat(result.asOfDate()).isEqualTo(TODAY);
        assertThat(result.totalCurrentQuantity()).isEqualByComparingTo("20");
        assertThat(result.availableQuantity()).isEqualByComparingTo("8");
        assertThat(result.minimumQuantity()).isEqualByComparingTo("10");
        assertThat(result.lowStock()).isTrue();
        assertThat(result.outOfStock()).isFalse();
        assertThat(result.nonZeroBatchCount()).isEqualTo(3);
        assertThat(result.nearestExpiration()).isEqualTo(TODAY.plusDays(5));
        assertThat(result.expiredBatchCount()).isEqualTo(1);
        assertThat(result.expiringSoonBatchCount()).isEqualTo(1);
    }

    @Test
    void shouldTreatZeroAvailabilityAsOutOfStockWithoutInventingMinimum() {
        var id = UUID.randomUUID();
        when(inventoryItemDetailsLookup.findById(id)).thenReturn(Optional.of(item(id, true)));
        when(inventoryItemOverviewQuery.findMetrics(id, TODAY, TODAY.plusDays(30)))
            .thenReturn(metrics("0", "0", null, 0, null, 0, 0));

        var result = getOverview.execute(new GetInventoryItemOverviewQuery(id, 30));

        assertThat(result.minimumQuantity()).isNull();
        assertThat(result.lowStock()).isFalse();
        assertThat(result.outOfStock()).isTrue();
    }

    @Test
    void shouldNotRaiseLowStockStateForInactiveItemLikeExistingLowStockAlerts() {
        var id = UUID.randomUUID();
        when(inventoryItemDetailsLookup.findById(id)).thenReturn(Optional.of(item(id, false)));
        when(inventoryItemOverviewQuery.findMetrics(id, TODAY, TODAY.plusDays(30)))
            .thenReturn(metrics("5", "5", "10", 1, null, 0, 0));

        assertThat(getOverview.execute(new GetInventoryItemOverviewQuery(id, 30)).lowStock()).isFalse();
    }

    @Test
    void shouldRejectUnknownItemBeforeInventoryProjection() {
        var id = UUID.randomUUID();
        when(inventoryItemDetailsLookup.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getOverview.execute(new GetInventoryItemOverviewQuery(id, 30)))
            .isInstanceOf(InventoryItemNotFoundException.class);
        verifyNoInteractions(inventoryItemOverviewQuery);
    }

    @Test
    void shouldRejectNegativeExpirationWindow() {
        assertThatThrownBy(() -> new GetInventoryItemOverviewQuery(UUID.randomUUID(), -1))
            .isInstanceOf(InvalidExpirationAlertWindowException.class);
    }

    private static InventoryItemDetails item(UUID id, boolean active) {
        return new InventoryItemDetails(id, "Lavender Essence", "ESSENCE", UnitOfMeasure.MILLILITER, active);
    }

    private static InventoryItemOverviewMetrics metrics(
        String total, String available, String minimum, long count,
        LocalDate nearest, long expired, long soon
    ) {
        return new InventoryItemOverviewMetrics(
            new BigDecimal(total), new BigDecimal(available), minimum == null ? null : new BigDecimal(minimum),
            count, nearest, expired, soon
        );
    }
}
