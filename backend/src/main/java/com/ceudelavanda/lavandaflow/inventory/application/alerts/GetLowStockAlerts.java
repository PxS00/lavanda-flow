package com.ceudelavanda.lavandaflow.inventory.application.alerts;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemLookup;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemSnapshot;
import com.ceudelavanda.lavandaflow.inventory.application.stock.AvailableStockBalance;
import com.ceudelavanda.lavandaflow.inventory.application.stock.AvailableStockQuery;
import com.ceudelavanda.lavandaflow.inventory.domain.MinimumStockLevel;
import com.ceudelavanda.lavandaflow.inventory.domain.MinimumStockLevelRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InventoryItemNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Retrieves informational low-stock alerts for active inventory items with a configured minimum. */
@Service
@RequiredArgsConstructor
public class GetLowStockAlerts {

    private static final BigDecimal ZERO_QUANTITY = BigDecimal.ZERO.setScale(6);
    private static final Comparator<LowStockAlertEntryResult> ALERT_ORDER = Comparator
        .comparing((LowStockAlertEntryResult alert) -> alert.availableQuantity().signum() != 0)
        .thenComparing(LowStockAlertEntryResult::deficitQuantity, Comparator.reverseOrder())
        .thenComparing(alert -> alert.name().toLowerCase(Locale.ROOT))
        .thenComparing(LowStockAlertEntryResult::inventoryItemId);

    private final MinimumStockLevelRepository minimumStockLevelRepository;
    private final InventoryItemLookup inventoryItemLookup;
    private final AvailableStockQuery availableStockQuery;
    private final Clock clock;

    @Transactional(readOnly = true)
    public LowStockAlertsResult execute() {
        return execute(LocalDate.now(clock));
    }

    /** Retrieves alerts using a caller-resolved business date. */
    @Transactional(readOnly = true)
    public LowStockAlertsResult execute(LocalDate asOfDate) {
        var levels = minimumStockLevelRepository.findAll();
        var itemIds = levels.stream().map(MinimumStockLevel::getInventoryItemId).toList();
        var itemsById = inventoryItemLookup.findByIds(itemIds).stream()
            .collect(java.util.stream.Collectors.toMap(InventoryItemSnapshot::id, Function.identity()));
        levels.forEach(level -> {
            if (!itemsById.containsKey(level.getInventoryItemId())) {
                throw new InventoryItemNotFoundException(level.getInventoryItemId());
            }
        });
        var activeLevels = levels.stream()
            .filter(level -> itemsById.get(level.getInventoryItemId()).active())
            .toList();
        var activeItemIds = activeLevels.stream().map(MinimumStockLevel::getInventoryItemId).toList();
        var availableBalances = activeItemIds.isEmpty()
            ? List.<AvailableStockBalance>of()
            : availableStockQuery.findAvailableStockByInventoryItemIds(activeItemIds, asOfDate);
        var availableByItemId = availableBalances.stream()
            .collect(java.util.stream.Collectors.toMap(AvailableStockBalance::inventoryItemId, AvailableStockBalance::availableQuantity));
        var alerts = activeLevels.stream()
            .map(level -> toAlert(level, itemsById.get(level.getInventoryItemId()), availableByItemId))
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .sorted(ALERT_ORDER)
            .toList();
        return new LowStockAlertsResult(asOfDate, alerts);
    }

    private static java.util.Optional<LowStockAlertEntryResult> toAlert(
        MinimumStockLevel level,
        InventoryItemSnapshot item,
        Map<UUID, BigDecimal> availableByItemId
    ) {
        var availableQuantity = availableByItemId.getOrDefault(level.getInventoryItemId(), ZERO_QUANTITY);
        if (availableQuantity.compareTo(level.getMinimumQuantity()) >= 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new LowStockAlertEntryResult(
            item.id(), item.name(), item.unitOfMeasure(), availableQuantity,
            level.getMinimumQuantity(), level.getMinimumQuantity().subtract(availableQuantity)
        ));
    }
}
