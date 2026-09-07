package com.ceudelavanda.lavandaflow.inventory.application.batch;

import com.ceudelavanda.lavandaflow.inventory.BatchDetails;
import com.ceudelavanda.lavandaflow.inventory.BatchDetailsLookup;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class InventoryBatchDetailsLookup implements BatchDetailsLookup {

    private final BatchRepository batchRepository;

    @Override
    public List<BatchDetails> findByIds(Collection<UUID> batchIds) {
        if (batchIds == null || batchIds.isEmpty()) {
            return List.of();
        }
        return batchRepository.findByIds(batchIds).stream()
            .map(InventoryBatchDetailsLookup::toDetails)
            .toList();
    }

    private static BatchDetails toDetails(Batch batch) {
        return new BatchDetails(
            batch.getId(),
            batch.getInventoryItemId(),
            batch.getSupplierId(),
            batch.getLotCode(),
            batch.getReceivedAt(),
            batch.getExpiresAt()
        );
    }
}
