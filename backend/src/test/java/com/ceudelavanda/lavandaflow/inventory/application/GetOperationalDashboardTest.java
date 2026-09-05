package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemLookup;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemSnapshot;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.application.alerts.GetExpirationAlerts;
import com.ceudelavanda.lavandaflow.inventory.application.alerts.GetLowStockAlerts;
import com.ceudelavanda.lavandaflow.inventory.application.dashboard.GetOperationalDashboard;
import com.ceudelavanda.lavandaflow.inventory.application.stock.AvailableStockBalance;
import com.ceudelavanda.lavandaflow.inventory.application.stock.AvailableStockQuery;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MinimumStockLevel;
import com.ceudelavanda.lavandaflow.inventory.domain.MinimumStockLevelRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOperationalDashboardTest {

    private static final LocalDate TODAY = LocalDate.of(2000, 1, 10);
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2000-01-10T12:00:00Z"),
        ZoneId.of("America/Sao_Paulo")
    );

    @Mock private InventoryItemLookup inventoryItemLookup;
    @Mock private AvailableStockQuery availableStockQuery;
    @Mock private MinimumStockLevelRepository minimumStockLevelRepository;
    @Mock private BatchRepository batchRepository;

    @Test
    void shouldComposeAllCountersUsingOneBusinessDateAndExistingAlertSemantics() {
        var noBalanceId = uuid(1);
        var exactMinimumId = uuid(2);
        var expiresTodayOnlyId = uuid(3);
        var inactiveId = uuid(4);
        var activeItems = List.of(
            item(noBalanceId, true),
            item(exactMinimumId, true),
            item(expiresTodayOnlyId, true)
        );
        var configuredIds = List.of(noBalanceId, exactMinimumId, expiresTodayOnlyId, inactiveId);
        when(inventoryItemLookup.findAllActive()).thenReturn(activeItems);
        when(minimumStockLevelRepository.findAll()).thenReturn(List.of(
            level(noBalanceId, "1"),
            level(exactMinimumId, "5"),
            level(expiresTodayOnlyId, "2"),
            level(inactiveId, "1")
        ));
        when(inventoryItemLookup.findByIds(configuredIds)).thenReturn(List.of(
            item(noBalanceId, true),
            item(exactMinimumId, true),
            item(expiresTodayOnlyId, true),
            item(inactiveId, false)
        ));
        when(availableStockQuery.findAvailableStockByInventoryItemIds(
            List.of(noBalanceId, exactMinimumId, expiresTodayOnlyId), TODAY
        )).thenReturn(List.of(new AvailableStockBalance(exactMinimumId, new BigDecimal("5.000000"))));
        when(batchRepository.findWithPositiveBalanceExpiringOnOrBefore(TODAY.plusDays(30)))
            .thenReturn(List.of(
                batch(expiresTodayOnlyId, 1, TODAY.minusDays(1)),
                batch(expiresTodayOnlyId, 2, TODAY),
                batch(exactMinimumId, 3, TODAY.plusDays(1)),
                batch(exactMinimumId, 4, TODAY.plusDays(30))
            ));
        var lowStock = new GetLowStockAlerts(
            minimumStockLevelRepository, inventoryItemLookup, availableStockQuery, CLOCK
        );
        var expiration = new GetExpirationAlerts(batchRepository, CLOCK);
        var dashboard = new GetOperationalDashboard(
            inventoryItemLookup, availableStockQuery, lowStock, expiration, CLOCK
        );

        var result = dashboard.execute(30);

        assertThat(result.asOfDate()).isEqualTo(TODAY);
        assertThat(result.expirationWindowDays()).isEqualTo(30);
        assertThat(result.activeItemCount()).isEqualTo(3);
        assertThat(result.lowStockItemCount()).isEqualTo(2);
        assertThat(result.outOfStockItemCount()).isEqualTo(2);
        assertThat(result.expiringSoonBatchCount()).isEqualTo(2);
        assertThat(result.expiredBatchCount()).isEqualTo(2);
        verify(availableStockQuery, times(2)).findAvailableStockByInventoryItemIds(
            List.of(noBalanceId, exactMinimumId, expiresTodayOnlyId), TODAY
        );
        verify(batchRepository).findWithPositiveBalanceExpiringOnOrBefore(TODAY.plusDays(30));
    }

    private static InventoryItemSnapshot item(UUID id, boolean active) {
        return new InventoryItemSnapshot(id, "Item " + id, UnitOfMeasure.UNIT, active);
    }

    private static MinimumStockLevel level(UUID id, String minimum) {
        return new MinimumStockLevel(id, new BigDecimal(minimum));
    }

    private static Batch batch(UUID itemId, long batchId, LocalDate expiresAt) {
        return new Batch(
            uuid(batchId + 100), itemId, null, "LOT-" + batchId,
            BigDecimal.ONE, BigDecimal.ONE, TODAY.minusMonths(1), expiresAt
        );
    }

    private static UUID uuid(long value) {
        return new UUID(0, value);
    }
}
