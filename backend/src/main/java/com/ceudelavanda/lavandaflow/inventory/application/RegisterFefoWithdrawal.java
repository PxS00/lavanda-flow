package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemLookup;
import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterFefoWithdrawalCommand;
import com.ceudelavanda.lavandaflow.inventory.application.result.FefoWithdrawalAllocationResult;
import com.ceudelavanda.lavandaflow.inventory.application.result.FefoWithdrawalResult;
import com.ceudelavanda.lavandaflow.inventory.domain.FefoAllocationPolicy;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovement;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InactiveInventoryItemException;
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
 * Application use case for an automatic, all-or-nothing FEFO withdrawal from
 * an inventory item.
 *
 * <p>The use case first confirms catalog availability and calculates the full
 * allocation plan. It mutates and persists batches only after the plan can
 * satisfy the complete request, creating one consumption movement per batch
 * within the same transaction.</p>
 */
@Service
@RequiredArgsConstructor
public class RegisterFefoWithdrawal {

    private final InventoryItemLookup inventoryItemLookup;
    private final BatchRepository batchRepository;
    private final StockMovementRepository stockMovementRepository;
    private final Clock clock;
    private final FefoAllocationPolicy fefoAllocationPolicy = new FefoAllocationPolicy();

    /**
     * Registers an automatic FEFO withdrawal for one active inventory item.
     *
     * <p>Expiration eligibility uses the business date provided by the injected
     * clock. Immutable movement timestamps remain absolute {@link Instant}s.</p>
     *
     * @throws InventoryItemNotFoundException if the item does not exist
     * @throws InactiveInventoryItemException if the item is inactive
     * @throws com.ceudelavanda.lavandaflow.inventory.domain.exception.InsufficientEligibleStockException
     * if eligible stock cannot satisfy the complete request
     */
    @Transactional
    public FefoWithdrawalResult execute(RegisterFefoWithdrawalCommand command) {
        var inventoryItem = inventoryItemLookup.findById(command.inventoryItemId())
            .orElseThrow(() -> new InventoryItemNotFoundException(command.inventoryItemId()));

        if (!inventoryItem.active()) {
            throw new InactiveInventoryItemException(command.inventoryItemId());
        }

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
