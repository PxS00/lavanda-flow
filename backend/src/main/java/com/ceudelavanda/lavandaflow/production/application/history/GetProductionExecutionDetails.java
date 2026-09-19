package com.ceudelavanda.lavandaflow.production.application.history;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemDetails;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemDetailsLookup;
import com.ceudelavanda.lavandaflow.inventory.BatchDetails;
import com.ceudelavanda.lavandaflow.inventory.BatchDetailsLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reads one immutable completed execution and bulk-resolves its display metadata. */
@Service
@RequiredArgsConstructor
public class GetProductionExecutionDetails {

    private final ProductionExecutionHistoryQuery historyQuery;
    private final InventoryItemDetailsLookup itemDetailsLookup;
    private final BatchDetailsLookup batchDetailsLookup;

    @Transactional(readOnly = true)
    public ProductionExecutionDetailsResult execute(UUID executionId) {
        var execution = historyQuery.findById(executionId)
            .orElseThrow(() -> new ProductionExecutionNotFoundException(executionId));

        var itemIds = new LinkedHashSet<UUID>();
        itemIds.add(execution.outputInventoryItemId());
        execution.consumptions().stream()
            .map(ProductionExecutionHistoryQuery.Consumption::sourceInventoryItemId)
            .forEach(itemIds::add);
        var itemsById = itemDetailsLookup.findByIds(itemIds).stream()
            .collect(Collectors.toMap(InventoryItemDetails::id, Function.identity()));

        var batchIds = execution.consumptions().stream()
            .map(ProductionExecutionHistoryQuery.Consumption::sourceBatchId)
            .distinct()
            .toList();
        var batchesById = batchDetailsLookup.findByIds(batchIds).stream()
            .collect(Collectors.toMap(BatchDetails::id, Function.identity()));

        var outputItem = requireItem(itemsById, execution.outputInventoryItemId());
        var consumptions = execution.consumptions().stream()
            .map(consumption -> toResult(consumption, itemsById, batchesById))
            .toList();

        return new ProductionExecutionDetailsResult(
            execution.executionId(), execution.formulaId(), execution.outputInventoryItemId(),
            outputItem.name(), outputItem.unitOfMeasure(), execution.outputBatchId(),
            execution.outputQuantity(), execution.lotCode(), execution.lotCodeMode(),
            execution.productionDate(), execution.outputReceivedAt(), execution.outputExpiresAt(),
            execution.completedAt(), consumptions
        );
    }

    private static ProductionExecutionDetailsResult.Consumption toResult(
        ProductionExecutionHistoryQuery.Consumption consumption,
        Map<UUID, InventoryItemDetails> itemsById,
        Map<UUID, BatchDetails> batchesById
    ) {
        var item = requireItem(itemsById, consumption.sourceInventoryItemId());
        var batch = batchesById.get(consumption.sourceBatchId());
        if (batch == null) {
            throw new IllegalStateException(
                "Completed production references missing source batch " + consumption.sourceBatchId()
            );
        }
        if (!batch.inventoryItemId().equals(consumption.sourceInventoryItemId())) {
            throw new IllegalStateException(
                "Completed production source batch/item identity mismatch for batch " + batch.id()
            );
        }
        return new ProductionExecutionDetailsResult.Consumption(
            consumption.sourceBatchId(), consumption.sourceInventoryItemId(), item.name(),
            item.unitOfMeasure(), batch.lotCode(), consumption.movementId(), consumption.quantity()
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
}
