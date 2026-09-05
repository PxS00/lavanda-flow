package com.ceudelavanda.lavandaflow.inventory.application.dashboard;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemLookup;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemSnapshot;
import com.ceudelavanda.lavandaflow.inventory.application.alerts.ExpirationAlertStatus;
import com.ceudelavanda.lavandaflow.inventory.application.alerts.GetExpirationAlerts;
import com.ceudelavanda.lavandaflow.inventory.application.alerts.GetExpirationAlertsQuery;
import com.ceudelavanda.lavandaflow.inventory.application.alerts.GetLowStockAlerts;
import com.ceudelavanda.lavandaflow.inventory.application.stock.AvailableStockBalance;
import com.ceudelavanda.lavandaflow.inventory.application.stock.AvailableStockQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds the read-only operational dashboard from existing inventory read semantics. */
@Service
@RequiredArgsConstructor
public class GetOperationalDashboard {

    private final InventoryItemLookup inventoryItemLookup;
    private final AvailableStockQuery availableStockQuery;
    private final GetLowStockAlerts getLowStockAlerts;
    private final GetExpirationAlerts getExpirationAlerts;
    private final Clock clock;

    @Transactional(readOnly = true)
    public OperationalDashboardSummary execute(int expirationWindowDays) {
        var asOfDate = LocalDate.now(clock);
        var activeItems = inventoryItemLookup.findAllActive();
        var activeItemIds = activeItems.stream().map(InventoryItemSnapshot::id).toList();
        var availableByItemId = availableStockQuery
            .findAvailableStockByInventoryItemIds(activeItemIds, asOfDate).stream()
            .collect(Collectors.toMap(AvailableStockBalance::inventoryItemId, Function.identity()));
        var lowStock = getLowStockAlerts.execute(asOfDate);
        var expiration = getExpirationAlerts.execute(
            new GetExpirationAlertsQuery(expirationWindowDays),
            asOfDate
        );

        var outOfStockItemCount = activeItemIds.stream()
            .filter(itemId -> !availableByItemId.containsKey(itemId)
                || availableByItemId.get(itemId).availableQuantity().signum() == 0)
            .count();
        var expiredBatchCount = expiration.alerts().stream()
            .filter(alert -> alert.status() == ExpirationAlertStatus.EXPIRED)
            .count();
        var expiringSoonBatchCount = expiration.alerts().stream()
            .filter(alert -> alert.status() == ExpirationAlertStatus.EXPIRING_SOON)
            .count();

        return new OperationalDashboardSummary(
            asOfDate,
            expirationWindowDays,
            activeItems.size(),
            lowStock.alerts().size(),
            outOfStockItemCount,
            expiringSoonBatchCount,
            expiredBatchCount
        );
    }
}
