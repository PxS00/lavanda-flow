package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterStockWithdrawalCommand;
import com.ceudelavanda.lavandaflow.inventory.application.result.StockMovementResult;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovement;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.BatchNotFoundException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InsufficientStockException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Application use case responsible for registering withdrawals from an
 * explicitly selected inventory batch.
 *
 * <p>The operation updates the batch balance and records the corresponding
 * stock movement within the same transaction.</p>
 */
@Service
@RequiredArgsConstructor
public class RegisterStockWithdrawal {

    private final BatchRepository batchRepository;
    private final StockMovementRepository stockMovementRepository;
    private final Clock clock;

    /**
     * Registers a positive withdrawal from one selected batch.
     *
     * @param command withdrawal data, including the selected batch
     * @return the persisted movement data and resulting batch balance
     * @throws BatchNotFoundException if the batch does not exist
     * @throws InsufficientStockException if the requested quantity exceeds the available balance
     * @throws IllegalArgumentException if the quantity violates domain invariants
     */
    @Transactional
    public StockMovementResult execute(RegisterStockWithdrawalCommand command) {
        var batch = batchRepository.findById(command.batchId())
            .orElseThrow(() -> new BatchNotFoundException(command.batchId()));

        batch.removeQuantity(command.quantity());

        var movement = StockMovement.create(
            batch.getId(),
            MovementType.CONSUMPTION,
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
