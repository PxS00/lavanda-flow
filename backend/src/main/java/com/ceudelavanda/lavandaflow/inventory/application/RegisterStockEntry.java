package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterStockEntryCommand;
import com.ceudelavanda.lavandaflow.inventory.application.result.StockMovementResult;
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
 * Application use case responsible for registering stock entries
 * for existing inventory batches.
 *
 * <p>The operation updates the batch balance and records the
 * corresponding stock movement within the same transaction.</p>
 */
@Service
@RequiredArgsConstructor
public class RegisterStockEntry {

    private final BatchRepository batchRepository;
    private final StockMovementRepository stockMovementRepository;
    private final Clock clock;

    /**
     * Registers a positive stock entry for an existing batch.
     *
     * @param command stock entry data
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
            batch.getId(),
            MovementType.ENTRY,
            command.quantity(),
            command.reason(),
            Instant.now(clock)
        );

        batchRepository.save(batch);
        stockMovementRepository.save(movement);

        return new StockMovementResult(
            movement.id(),
            batch.getId(),
            movement.type(),
            movement.quantity(),
            batch.getCurrentQuantity(),
            movement.reason(),
            movement.occurredAt()
        );
    }
}
