package com.ceudelavanda.lavandaflow.inventory.application.production;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemOperationLock;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemSnapshot;
import com.ceudelavanda.lavandaflow.inventory.*;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovement;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.StockQuantityRules;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Inventory-owned atomic application service for exact production stock effects. */
@Service
@RequiredArgsConstructor
class ApplyProductionStock implements ProductionStockApplication {

    private final InventoryItemOperationLock inventoryItemOperationLock;
    private final BatchRepository batchRepository;
    private final StockMovementRepository stockMovementRepository;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProductionStockResult apply(ProductionStockCommand command) {
        var sourceAllocations = validate(command);
        var sourceItemIds = findSourceItemIds(sourceAllocations);
        var itemSnapshots = lockInventoryItems(sourceItemIds, command.outputBatch().inventoryItemId());
        lockSourceBatches(sourceAllocations);
        var sourceBatches = reloadSourceBatches(sourceAllocations);
        var businessDate = LocalDate.now(clock);

        validateSources(sourceAllocations, sourceBatches, itemSnapshots, businessDate);
        var output = Batch.create(
            command.outputBatch().inventoryItemId(),
            null,
            command.outputBatch().lotCode(),
            command.outputBatch().quantity(),
            command.outputBatch().receivedAt(),
            command.outputBatch().expiresAt()
        );
        var occurredAt = Instant.now(clock);
        var consumptions = consumeSources(sourceAllocations, sourceBatches, occurredAt);
        var outputMovement = StockMovement.create(
            output.getId(), MovementType.ENTRY, output.getInitialQuantity(), null, occurredAt
        );

        batchRepository.save(output);
        stockMovementRepository.save(outputMovement);
        return new ProductionStockResult(output.getId(), outputMovement.id(), consumptions);
    }

    private List<ProductionSourceAllocation> validate(ProductionStockCommand command) {
        if (command == null || command.outputBatch() == null) {
            throw new IllegalArgumentException("production stock command and outputBatch must not be null");
        }
        if (command.sourceAllocations() == null || command.sourceAllocations().isEmpty()) {
            throw new IllegalArgumentException("sourceAllocations must not be empty");
        }
        if (command.outputBatch().inventoryItemId() == null || command.outputBatch().receivedAt() == null) {
            throw new IllegalArgumentException("output inventoryItemId and receivedAt must not be null");
        }
        StockQuantityRules.requirePositive(command.outputBatch().quantity(), "outputBatch.quantity");

        var seenBatchIds = new HashSet<UUID>();
        var allocations = new ArrayList<ProductionSourceAllocation>();
        for (var allocation : command.sourceAllocations()) {
            if (allocation == null || allocation.batchId() == null) {
                throw new IllegalArgumentException("source allocation and batchId must not be null");
            }
            StockQuantityRules.requirePositive(allocation.quantity(), "sourceAllocation.quantity");
            if (!seenBatchIds.add(allocation.batchId())) {
                throw new IllegalArgumentException("sourceAllocations must not contain duplicate batch IDs");
            }
            allocations.add(allocation);
        }
        allocations.sort(Comparator.comparing(ProductionSourceAllocation::batchId));
        return allocations;
    }

    private Set<UUID> findSourceItemIds(List<ProductionSourceAllocation> sourceAllocations) {
        var sourceItemIds = new TreeSet<UUID>();
        for (var allocation : sourceAllocations) {
            var inventoryItemId = batchRepository.findInventoryItemIdById(allocation.batchId())
                .orElseThrow(() -> new BatchNotFoundException(allocation.batchId()));
            sourceItemIds.add(inventoryItemId);
        }
        return sourceItemIds;
    }

    private Map<UUID, InventoryItemSnapshot> lockInventoryItems(Set<UUID> sourceItemIds, UUID outputItemId) {
        var itemIds = new TreeSet<>(sourceItemIds);
        itemIds.add(outputItemId);
        var snapshots = new HashMap<UUID, InventoryItemSnapshot>();
        for (var itemId : itemIds) {
            var item = inventoryItemOperationLock.lockById(itemId)
                .orElseThrow(() -> new InventoryItemNotFoundException(itemId));
            if (!item.active()) {
                throw new InactiveInventoryItemException(itemId);
            }
            snapshots.put(itemId, item);
        }
        return snapshots;
    }

    private void lockSourceBatches(List<ProductionSourceAllocation> sourceAllocations) {
        sourceAllocations.forEach(allocation -> batchRepository.lockByIdForUpdate(allocation.batchId()));
    }

    private Map<UUID, Batch> reloadSourceBatches(List<ProductionSourceAllocation> sourceAllocations) {
        var batches = new HashMap<UUID, Batch>();
        for (var allocation : sourceAllocations) {
            var batch = batchRepository.findById(allocation.batchId())
                .orElseThrow(() -> new BatchNotFoundException(allocation.batchId()));
            batches.put(batch.getId(), batch);
        }
        return batches;
    }

    private void validateSources(
        List<ProductionSourceAllocation> sourceAllocations,
        Map<UUID, Batch> sourceBatches,
        Map<UUID, InventoryItemSnapshot> itemSnapshots,
        LocalDate businessDate
    ) {
        for (var allocation : sourceAllocations) {
            var batch = sourceBatches.get(allocation.batchId());
            if (!itemSnapshots.containsKey(batch.getInventoryItemId())) {
                throw new InventoryItemNotFoundException(batch.getInventoryItemId());
            }
            if (batch.getExpiresAt() != null && !batch.getExpiresAt().isAfter(businessDate)) {
                throw new ExpiredBatchException(batch.getId());
            }
            if (batch.getCurrentQuantity().compareTo(allocation.quantity()) < 0) {
                throw new InsufficientStockException(batch.getId(), allocation.quantity(), batch.getCurrentQuantity());
            }
        }
    }

    private List<ProductionSourceConsumptionResult> consumeSources(
        List<ProductionSourceAllocation> sourceAllocations,
        Map<UUID, Batch> sourceBatches,
        Instant occurredAt
    ) {
        var results = new ArrayList<ProductionSourceConsumptionResult>();
        for (var allocation : sourceAllocations) {
            var batch = sourceBatches.get(allocation.batchId());
            batch.removeQuantity(allocation.quantity());
            var movement = StockMovement.create(
                batch.getId(), MovementType.CONSUMPTION, allocation.quantity(), null, occurredAt
            );
            batchRepository.save(batch);
            stockMovementRepository.save(movement);
            results.add(new ProductionSourceConsumptionResult(batch.getId(), movement.id(), allocation.quantity()));
        }
        return results;
    }
}
