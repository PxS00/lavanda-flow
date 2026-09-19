package com.ceudelavanda.lavandaflow.production.application.history;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemDetails;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemDetailsLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reads immutable completed-production summaries and bulk-resolves current catalog metadata. */
@Service
@RequiredArgsConstructor
public class GetProductionExecutionHistory {

    private final ProductionExecutionHistoryQuery historyQuery;
    private final InventoryItemDetailsLookup itemDetailsLookup;

    @Transactional(readOnly = true)
    public ProductionExecutionHistoryResult execute(GetProductionExecutionHistoryQuery query) {
        var page = historyQuery.find(query);
        var itemIds = page.content().stream()
            .map(ProductionExecutionHistoryQuery.Summary::outputInventoryItemId)
            .distinct()
            .toList();
        var itemsById = itemIds.isEmpty()
            ? Map.<UUID, InventoryItemDetails>of()
            : itemDetailsLookup.findByIds(itemIds).stream()
                .collect(Collectors.toMap(InventoryItemDetails::id, Function.identity()));
        var content = page.content().stream()
            .map(entry -> toResult(entry, requireItem(itemsById, entry.outputInventoryItemId())))
            .toList();

        return new ProductionExecutionHistoryResult(
            content, page.page(), page.size(), page.totalElements(), page.totalPages()
        );
    }

    private static InventoryItemDetails requireItem(
        Map<UUID, InventoryItemDetails> itemsById,
        UUID inventoryItemId
    ) {
        var item = itemsById.get(inventoryItemId);
        if (item == null) {
            throw new IllegalStateException(
                "Completed production references missing catalog item " + inventoryItemId
            );
        }
        return item;
    }

    private static ProductionExecutionHistoryResult.Entry toResult(
        ProductionExecutionHistoryQuery.Summary entry,
        InventoryItemDetails item
    ) {
        return new ProductionExecutionHistoryResult.Entry(
            entry.executionId(), entry.formulaId(), entry.outputInventoryItemId(), item.name(),
            item.unitOfMeasure(), entry.outputBatchId(), entry.outputQuantity(), entry.lotCode(),
            entry.lotCodeMode(), entry.productionDate(), entry.completedAt()
        );
    }
}
