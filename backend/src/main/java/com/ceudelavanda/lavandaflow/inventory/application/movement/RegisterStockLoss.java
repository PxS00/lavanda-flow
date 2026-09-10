package com.ceudelavanda.lavandaflow.inventory.application.movement;

import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovement;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.BatchNotFoundException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InvalidStockMovementReasonException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Registers an auditable physical loss for one selected inventory batch. */
@Service
@RequiredArgsConstructor
public class RegisterStockLoss {

    private final BatchRepository batchRepository;
    private final StockMovementRepository stockMovementRepository;
    private final Clock clock;

    /**
     * Decrements the selected batch and persists its immutable loss movement atomically.
     *
     * @throws BatchNotFoundException if the batch does not exist
     * @throws InvalidStockMovementReasonException if the audit reason is blank or too long
     */
    @Transactional
    public StockMovementResult execute(RegisterStockLossCommand command) {
        requireReason(command.reason());
        batchRepository.lockByIdForUpdate(command.batchId());
        var batch = batchRepository.findById(command.batchId())
            .orElseThrow(() -> new BatchNotFoundException(command.batchId()));
        batch.removeQuantity(command.quantity());
        var movement = StockMovement.create(
            batch.getId(), MovementType.LOSS, command.quantity(), command.reason(), Instant.now(clock)
        );
        batchRepository.save(batch);
        stockMovementRepository.save(movement);
        return new StockMovementResult(
            movement.id(), batch.getId(), movement.type(), movement.quantity(), batch.getCurrentQuantity(),
            movement.reason(), movement.occurredAt()
        );
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 255) {
            throw new InvalidStockMovementReasonException();
        }
    }
}
