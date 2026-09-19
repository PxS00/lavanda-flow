package com.ceudelavanda.lavandaflow.inventory.application.overview;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemStockLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;

@Service
@RequiredArgsConstructor
public class GetInventoryStockList {

    private final InventoryItemStockLookup inventoryItemStockLookup;
    private final InventoryItemOverviewQuery inventoryItemOverviewQuery;
    private final Clock clock;

    @Transactional(readOnly = true)
    public InventoryStockListResult execute(InventoryStockListQuery query) {
        var asOfDate = LocalDate.now(clock);
        var catalogPage = inventoryItemStockLookup.findPage(
            new InventoryItemStockLookup.Query(query.categories(), query.page(), query.size())
        );
        var ids = catalogPage.content().stream()
            .map(InventoryItemStockLookup.Item::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        var metricsById = inventoryItemOverviewQuery.findMetrics(
            ids, asOfDate, asOfDate.plusDays(query.expirationWindowDays())
        );
        var content = catalogPage.content().stream().map(item -> {
            var metrics = metricsById.getOrDefault(item.id(), InventoryItemOverviewMetrics.zero(null));
            return new InventoryStockListResult.Entry(
                item.id(), item.name(), item.category(), item.unitOfMeasure(), item.active(),
                item.essenceReference(), item.productionTypeCode(), metrics.totalCurrentQuantity(),
                metrics.availableQuantity(), metrics.minimumQuantity(), metrics.lowStock(item.active()),
                metrics.outOfStock(), metrics.nonZeroBatchCount(), metrics.nearestExpiration()
            );
        }).toList();
        return new InventoryStockListResult(
            content, catalogPage.page(), catalogPage.size(), catalogPage.totalElements(), catalogPage.totalPages(),
            asOfDate, query.expirationWindowDays()
        );
    }
}
