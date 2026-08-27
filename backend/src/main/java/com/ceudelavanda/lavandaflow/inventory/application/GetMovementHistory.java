package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemLookup;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemSnapshot;
import com.ceudelavanda.lavandaflow.inventory.application.query.GetMovementHistoryQuery;
import com.ceudelavanda.lavandaflow.inventory.application.result.MovementHistoryEntryResult;
import com.ceudelavanda.lavandaflow.inventory.application.result.MovementHistoryResult;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InventoryItemNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Retrieves immutable stock-movement audit history and enriches entries through the public catalog API.
 *
 * <p>The persistence read model owns inventory joins only. Catalog details are resolved through
 * {@link InventoryItemLookup}, preserving module boundaries while keeping inactive items visible
 * in historical results.</p>
 */
@Service
@RequiredArgsConstructor
public class GetMovementHistory {

    private final MovementHistoryQuery movementHistoryQuery;
    private final InventoryItemLookup inventoryItemLookup;

    @Transactional(readOnly = true)
    public MovementHistoryResult execute(GetMovementHistoryQuery query) {
        var page = movementHistoryQuery.find(query);
        var itemIds = page.content().stream()
            .map(MovementHistoryEntry::inventoryItemId)
            .distinct()
            .toList();
        var itemsById = findItemsById(itemIds);

        var content = page.content().stream()
            .map(entry -> toResult(entry, requireItem(itemsById, entry.inventoryItemId())))
            .toList();

        return new MovementHistoryResult(
            content,
            page.page(),
            page.size(),
            page.totalElements(),
            page.totalPages()
        );
    }

    private Map<UUID, InventoryItemSnapshot> findItemsById(java.util.List<UUID> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }

        return inventoryItemLookup.findByIds(itemIds).stream()
            .collect(Collectors.toMap(InventoryItemSnapshot::id, Function.identity()));
    }

    private static InventoryItemSnapshot requireItem(
        Map<UUID, InventoryItemSnapshot> itemsById,
        UUID inventoryItemId
    ) {
        var item = itemsById.get(inventoryItemId);
        if (item == null) {
            throw new InventoryItemNotFoundException(inventoryItemId);
        }
        return item;
    }

    private static MovementHistoryEntryResult toResult(
        MovementHistoryEntry entry,
        InventoryItemSnapshot item
    ) {
        return new MovementHistoryEntryResult(
            entry.movementId(),
            entry.inventoryItemId(),
            item.name(),
            item.unitOfMeasure(),
            item.active(),
            entry.batchId(),
            entry.lotCode(),
            entry.type(),
            entry.quantity(),
            entry.reason(),
            entry.occurredAt()
        );
    }
}
