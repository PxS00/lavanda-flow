package com.ceudelavanda.lavandaflow.inventory.application.overview;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemStockLookup;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetInventoryStockListTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 18);
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-09-18T15:00:00Z"), ZoneId.of("America/Sao_Paulo")
    );

    @Mock private InventoryItemStockLookup catalog;
    @Mock private InventoryItemOverviewQuery metricsQuery;
    private GetInventoryStockList useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetInventoryStockList(catalog, metricsQuery, CLOCK);
    }

    @Test
    void shouldMergeBulkMetricsByIdWithoutChangingCatalogOrderOrPageMetadata() {
        var first = item("Produto a granel", true, "027", "BULK");
        var second = item("Produto 30 ml", true, "027", "PACKAGED");
        var query = new InventoryStockListQuery(List.of("FINISHED_PRODUCT"), 1, 2, 30);
        when(catalog.findPage(new InventoryItemStockLookup.Query(query.categories(), 1, 2)))
            .thenReturn(new InventoryItemStockLookup.Page(List.of(first, second), 1, 2, 4, 2));
        when(metricsQuery.findMetrics(Set.of(first.id(), second.id()), TODAY, TODAY.plusDays(30)))
            .thenReturn(Map.of(
                second.id(), metrics("2.000000", "2.000000", null, false),
                first.id(), metrics("10.125000", "4.500000", "5.000000", true)
            ));

        var result = useCase.execute(query);

        assertThat(result.content()).extracting(InventoryStockListResult.Entry::inventoryItemId)
            .containsExactly(first.id(), second.id());
        assertThat(result.content().get(0).totalCurrentQuantity()).isEqualByComparingTo("10.125000");
        assertThat(result.content().get(0).availableQuantity()).isEqualByComparingTo("4.500000");
        assertThat(result.content().get(0).lowStock()).isTrue();
        assertThat(result.content().get(0).essenceReference()).isEqualTo("027");
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(4);
        assertThat(result.asOfDate()).isEqualTo(TODAY);
    }

    @Test
    void shouldZeroMissingMetricsAndNeverFlagInactiveItemAsLowStock() {
        var item = item("Inactive", false, null, null);
        var query = new InventoryStockListQuery(List.of(), 0, 20, 30);
        when(catalog.findPage(new InventoryItemStockLookup.Query(List.of(), 0, 20)))
            .thenReturn(new InventoryItemStockLookup.Page(List.of(item), 0, 20, 1, 1));
        when(metricsQuery.findMetrics(Set.of(item.id()), TODAY, TODAY.plusDays(30))).thenReturn(Map.of());

        var row = useCase.execute(query).content().getFirst();

        assertThat(row.totalCurrentQuantity()).isEqualByComparingTo("0.000000");
        assertThat(row.availableQuantity()).isEqualByComparingTo("0.000000");
        assertThat(row.outOfStock()).isTrue();
        assertThat(row.lowStock()).isFalse();
    }

    @Test
    void shouldFlagActiveMinimumOnlyItemAsLowAndOutOfStock() {
        var item = item("Minimum only", true, null, null);
        var query = new InventoryStockListQuery(List.of(), 0, 20, 30);
        when(catalog.findPage(new InventoryItemStockLookup.Query(List.of(), 0, 20)))
            .thenReturn(new InventoryItemStockLookup.Page(List.of(item), 0, 20, 1, 1));
        when(metricsQuery.findMetrics(Set.of(item.id()), TODAY, TODAY.plusDays(30)))
            .thenReturn(Map.of(item.id(), InventoryItemOverviewMetrics.zero(new BigDecimal("4.000000"))));

        var row = useCase.execute(query).content().getFirst();

        assertThat(row.totalCurrentQuantity()).isEqualByComparingTo("0.000000");
        assertThat(row.availableQuantity()).isEqualByComparingTo("0.000000");
        assertThat(row.lowStock()).isTrue();
        assertThat(row.outOfStock()).isTrue();
    }

    @Test
    void shouldPreserveExpiredPhysicalStockAndBackendAvailabilityStatus() {
        var item = item("Expired stock", true, null, null);
        var query = new InventoryStockListQuery(List.of(), 0, 20, 30);
        when(catalog.findPage(new InventoryItemStockLookup.Query(List.of(), 0, 20)))
            .thenReturn(new InventoryItemStockLookup.Page(List.of(item), 0, 20, 1, 1));
        when(metricsQuery.findMetrics(Set.of(item.id()), TODAY, TODAY.plusDays(30)))
            .thenReturn(Map.of(item.id(), new InventoryItemOverviewMetrics(
                new BigDecimal("5.000000"), new BigDecimal("0.000000"), new BigDecimal("10.000000"),
                1, null, 1, 0
            )));

        var row = useCase.execute(query).content().getFirst();

        assertThat(row.totalCurrentQuantity()).isEqualByComparingTo("5.000000");
        assertThat(row.availableQuantity()).isEqualByComparingTo("0.000000");
        assertThat(row.lowStock()).isTrue();
        assertThat(row.outOfStock()).isTrue();
    }

    private static InventoryItemStockLookup.Item item(
        String name, boolean active, String essenceReference, String productionTypeCode
    ) {
        return new InventoryItemStockLookup.Item(
            UUID.randomUUID(), name, "FINISHED_PRODUCT", UnitOfMeasure.MILLILITER, active,
            essenceReference, productionTypeCode
        );
    }

    private static InventoryItemOverviewMetrics metrics(
        String total, String available, String minimum, boolean withExpiration
    ) {
        return new InventoryItemOverviewMetrics(
            new BigDecimal(total), new BigDecimal(available),
            minimum == null ? null : new BigDecimal(minimum), 1,
            withExpiration ? TODAY.plusDays(5) : null, 1, 1
        );
    }
}
