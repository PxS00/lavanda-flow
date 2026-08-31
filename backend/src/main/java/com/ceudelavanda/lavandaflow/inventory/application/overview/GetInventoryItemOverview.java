package com.ceudelavanda.lavandaflow.inventory.application.overview;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemDetailsLookup;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InventoryItemNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/** Builds a compact operational read model for one inventory item. */
@Service
@RequiredArgsConstructor
public class GetInventoryItemOverview {

    private final InventoryItemDetailsLookup inventoryItemDetailsLookup;
    private final InventoryItemOverviewQuery inventoryItemOverviewQuery;
    private final Clock clock;

    @Transactional(readOnly = true)
    public InventoryItemOverviewResult execute(GetInventoryItemOverviewQuery query) {
        var item = inventoryItemDetailsLookup.findById(query.inventoryItemId())
            .orElseThrow(() -> new InventoryItemNotFoundException(query.inventoryItemId()));
        var asOfDate = LocalDate.now(clock);
        var expirationCutoff = asOfDate.plusDays(query.expirationWindowDays());
        var metrics = inventoryItemOverviewQuery.findMetrics(item.id(), asOfDate, expirationCutoff);
        var outOfStock = metrics.availableQuantity().signum() == 0;
        var lowStock = item.active()
            && metrics.minimumQuantity() != null
            && metrics.availableQuantity().compareTo(metrics.minimumQuantity()) < 0;

        return new InventoryItemOverviewResult(
            item.id(),
            item.name(),
            item.category(),
            item.unitOfMeasure(),
            item.active(),
            asOfDate,
            query.expirationWindowDays(),
            metrics.totalCurrentQuantity(),
            metrics.availableQuantity(),
            metrics.minimumQuantity(),
            lowStock,
            outOfStock,
            metrics.nonZeroBatchCount(),
            metrics.nearestExpiration(),
            metrics.expiredBatchCount(),
            metrics.expiringSoonBatchCount()
        );
    }
}
