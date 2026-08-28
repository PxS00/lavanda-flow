package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registers a new inventory catalog item. */
@Service
@RequiredArgsConstructor
public class RegisterInventoryItem {

    private final InventoryItemRepository inventoryItemRepository;

    @Transactional
    public InventoryItemResult execute(RegisterInventoryItemCommand command) {
        var item = InventoryItem.create(
            command.name(),
            command.description(),
            command.category(),
            command.unitOfMeasure()
        );
        return InventoryItemResult.from(inventoryItemRepository.save(item));
    }
}
