package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterStockAdjustmentCommand;
import com.ceudelavanda.lavandaflow.inventory.application.result.StockMovementResult;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovement;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.BatchNotFoundException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InsufficientStockException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InvalidStockAdjustmentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Application use case responsible for registering signed adjustments for an
 * explicitly selected inventory batch.
 *
 * <p>The operation updates the batch balance and records the corresponding
 * stock movement within the same transaction.</p>
 */
@Service
@RequiredArgsConstructor
public class RegisterStockAdjustment {

    private final BatchRepository batchRepository;
    private final StockMovementRepository stockMovementRepository;
    private final Clock clock;

    /**
     * Registers a signed adjustment for one selected batch.
     *
     * @param command adjustment data, including the selected batch and audit reason
     * @return the persisted movement data and resulting batch balance
     * @throws BatchNotFoundException if the batch does not exist
     * @throws InvalidStockAdjustmentException if the adjustment is zero
     * @throws InsufficientStockException if a negative adjustment exceeds the available balance
     */
    @Transactional
    public StockMovementResult execute(RegisterStockAdjustmentCommand command) {
        requireReason(command.reason());
        batchRepository.lockByIdForUpdate(command.batchId());

        var batch = batchRepository.findById(command.batchId())
            .orElseThrow(() -> new BatchNotFoundException(command.batchId()));

        batch.adjustQuantity(command.quantity());

        var movementType = command.quantity().signum() > 0
            ? MovementType.ADJUSTMENT_IN
            : MovementType.ADJUSTMENT_OUT;

        var movement = StockMovement.create(
            batch.getId(),
            movementType,
            command.quantity().abs(),
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

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidStockAdjustmentException(
                "Stock adjustment reason must not be blank"
            );
        }
    }
}
