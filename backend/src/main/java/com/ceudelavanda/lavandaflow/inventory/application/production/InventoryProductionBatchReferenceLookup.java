package com.ceudelavanda.lavandaflow.inventory.application.production;

import com.ceudelavanda.lavandaflow.inventory.ProductionBatchReference;
import com.ceudelavanda.lavandaflow.inventory.ProductionBatchReferenceLookup;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class InventoryProductionBatchReferenceLookup implements ProductionBatchReferenceLookup {

    private final BatchRepository batchRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductionBatchReference> findByBatchId(UUID batchId) {
        return batchRepository.findInventoryItemIdById(batchId)
            .map(inventoryItemId -> new ProductionBatchReference(batchId, inventoryItemId));
    }
}
