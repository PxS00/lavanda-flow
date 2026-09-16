package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maintains the explicitly supported metadata of an existing catalog item. */
@Service
@RequiredArgsConstructor
public class UpdateInventoryItem {

    private final InventoryItemRepository inventoryItemRepository;

    @Transactional
    public InventoryItemResult execute(UpdateInventoryItemCommand command) {
        var item = inventoryItemRepository.findById(command.inventoryItemId())
            .orElseThrow(() -> new InventoryItemNotFoundException(command.inventoryItemId()));

        item.rename(command.name());
        item.changeDescription(command.description());
        assignEssenceReference(item, command.essenceReference());
        assignProductionTypeCode(item, command.productionTypeCode());
        if (command.active()) {
            item.activate();
        } else {
            item.deactivate();
        }

        return InventoryItemResult.from(inventoryItemRepository.save(item));
    }

    private static void assignEssenceReference(InventoryItem item, String reference) {
        try {
            item.assignEssenceReference(reference);
        } catch (IllegalStateException exception) {
            throw InvalidInventoryItemMaintenanceException.immutable("essenceReference");
        } catch (IllegalArgumentException exception) {
            throw InvalidInventoryItemMaintenanceException.invalid("essenceReference");
        }
    }

    private static void assignProductionTypeCode(InventoryItem item, String code) {
        try {
            item.assignProductionTypeCode(code);
        } catch (IllegalStateException exception) {
            throw InvalidInventoryItemMaintenanceException.immutable("productionTypeCode");
        } catch (IllegalArgumentException exception) {
            throw InvalidInventoryItemMaintenanceException.invalid("productionTypeCode");
        }
    }
}
