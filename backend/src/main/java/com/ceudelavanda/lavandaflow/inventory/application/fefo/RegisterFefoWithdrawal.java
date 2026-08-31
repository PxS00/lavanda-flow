package com.ceudelavanda.lavandaflow.inventory.application.fefo;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemOperationLock;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.FefoAllocationPolicy;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovement;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InactiveInventoryItemException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InsufficientEligibleStockException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InventoryItemNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

/**
 * Application use case for an automatic, all-or-nothing FEFO withdrawal from an inventory item.
 *
 * <p>The use case first acquires the catalog-owned item serialization point, then pessimistically locks
 * all current batches for the item in deterministic UUID order. This prevents a concurrent receipt from
 * inserting a new batch while FEFO is deciding from the eligible-batch set. The pure allocation policy
 * runs only after that set is stable, and every balance change and immutable movement commits atomically.</p>
 */
@Service
@RequiredArgsConstructor
public class RegisterFefoWithdrawal {

    private final InventoryItemOperationLock inventoryItemOperationLock;
    private final BatchRepository batchRepository;
    private final StockMovementRepository stockMovementRepository;
    private final Clock clock;
    private final FefoAllocationPolicy fefoAllocationPolicy = new FefoAllocationPolicy();

    /**
     * Registers an automatic FEFO withdrawal for one active inventory item.
     *
     * <p>Concurrent receipts for the same item wait on the same item-level lock. After the competing
     * transaction commits, PostgreSQL READ COMMITTED makes the fresh batch set visible to the next
     * statement. There is no automatic application retry.</p>
     *
     * @param command requested item, quantity, and optional audit reason
     * @return the complete committed allocation result
     * @throws InventoryItemNotFoundException if the item does not exist
     * @throws InactiveInventoryItemException if the item is inactive
     * @throws InsufficientEligibleStockException if eligible stock cannot satisfy the complete request
     */
    @Transactional
    public FefoWithdrawalResult execute(RegisterFefoWithdrawalCommand command) {
        var inventoryItem = inventoryItemOperationLock.lockById(command.inventoryItemId())
            .orElseThrow(() -> new InventoryItemNotFoundException(command.inventoryItemId()));

        if (!inventoryItem.active()) {
            throw new InactiveInventoryItemException(command.inventoryItemId());
        }

        batchRepository.lockByInventoryItemIdForUpdate(command.inventoryItemId());
        var batches = batchRepository.findByInventoryItemId(command.inventoryItemId());
        var businessDate = LocalDate.now(clock);
        var allocationPlan = fefoAllocationPolicy.allocate(
            command.inventoryItemId(),
            batches,
            command.quantity(),
            businessDate
        );
        var batchesById = new HashMap<UUID, com.ceudelavanda.lavandaflow.inventory.domain.Batch>();
        batches.forEach(batch -> batchesById.put(batch.getId(), batch));
        var occurredAt = Instant.now(clock);
        var allocationResults = new ArrayList<FefoWithdrawalAllocationResult>();

        for (var allocation : allocationPlan.allocations()) {
            var batch = batchesById.get(allocation.batchId());
            batch.removeQuantity(allocation.quantity());

            var movement = StockMovement.create(
                batch.getId(),
                MovementType.CONSUMPTION,
                allocation.quantity(),
                command.reason(),
                occurredAt
            );

            batchRepository.save(batch);
            stockMovementRepository.save(movement);
            allocationResults.add(new FefoWithdrawalAllocationResult(
                batch.getId(),
                movement.id(),
                allocation.quantity()
            ));
        }

        return new FefoWithdrawalResult(
            allocationPlan.inventoryItemId(),
            allocationPlan.requestedQuantity(),
            allocationPlan.allocatedQuantity(),
            allocationResults
        );
    }
}
