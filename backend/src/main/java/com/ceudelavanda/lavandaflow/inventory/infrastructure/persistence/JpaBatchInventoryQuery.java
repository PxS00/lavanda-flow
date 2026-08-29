package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.inventory.application.batch.BatchInventoryQuery;
import com.ceudelavanda.lavandaflow.inventory.application.batch.BatchInventoryRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JpaBatchInventoryQuery implements BatchInventoryQuery {

    private final SpringDataBatchRepository repository;

    @Override
    public List<BatchInventoryRecord> findByInventoryItemId(UUID inventoryItemId) {
        return repository.findBatchInventoryByInventoryItemId(inventoryItemId);
    }
}
