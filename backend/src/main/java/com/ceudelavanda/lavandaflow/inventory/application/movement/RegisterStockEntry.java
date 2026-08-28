package com.ceudelavanda.lavandaflow.inventory.application.movement;

import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovement;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.BatchNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Application use case responsible for registering stock entries for an explicitly selected inventory batch.
 *
 * <p>The batch balance update and immutable movement record are persisted within the same transaction.
 * The target batch is pessimistically locked before it is read so concurrent stock mutations cannot
 * overwrite each other.</p>
 */
@Service
@RequiredArgsConstructor
public class RegisterStockEntry {

    private final BatchRepository batchRepository;
    private final StockMovementRepository stockMovementRepository;
    private final Clock clock;

    /**
     * Registers a positive stock entry for one existing batch.
     *
     * @param command entry data, including the selected batch and quantity
     * @return the persisted movement data and resulting batch balance
     * @throws BatchNotFoundException if the batch does not exist
     * @throws IllegalArgumentException if the quantity violates domain invariants
     */
    @Transactional
    public StockMovementResult execute(RegisterStockEntryCommand command) {
        batchRepository.lockByIdForUpdate(command.batchId());
        var batch = batchRepository.findById(command.batchId())
            .orElseThrow(() -> new BatchNotFoundException(command.batchId()));
        batch.addQuantity(command.quantity());
        var movement = StockMovement.create(
            batch.getId(), MovementType.ENTRY, command.quantity(), command.reason(), Instant.now(clock)
        );
        batchRepository.save(batch);
        stockMovementRepository.save(movement);
        return new StockMovementResult(
            movement.id(), batch.getId(), movement.type(), movement.quantity(), batch.getCurrentQuantity(),
            movement.reason(), movement.occurredAt()
        );
    }
}
