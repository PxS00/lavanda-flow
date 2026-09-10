package com.ceudelavanda.lavandaflow.inventory.application.movement;

import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovement;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.BatchNotExpiredException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.BatchNotFoundException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InvalidStockMovementReasonException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/** Registers an auditable disposal for a selected batch that is already expired. */
@Service
@RequiredArgsConstructor
public class RegisterExpiredStockDisposal {

    private final BatchRepository batchRepository;
    private final StockMovementRepository stockMovementRepository;
    private final Clock clock;

    /**
     * Decrements an expired batch and persists its immutable disposal movement atomically.
     *
     * @throws BatchNotFoundException if the batch does not exist
     * @throws BatchNotExpiredException if the batch has no expiration date or expires after today
     * @throws InvalidStockMovementReasonException if the audit reason is blank or too long
     */
    @Transactional
    public StockMovementResult execute(RegisterExpiredStockDisposalCommand command) {
        requireReason(command.reason());
        batchRepository.lockByIdForUpdate(command.batchId());
        var batch = batchRepository.findById(command.batchId())
            .orElseThrow(() -> new BatchNotFoundException(command.batchId()));
        if (batch.getExpiresAt() == null || batch.getExpiresAt().isAfter(LocalDate.now(clock))) {
            throw new BatchNotExpiredException(batch.getId());
        }
        batch.removeQuantity(command.quantity());
        var movement = StockMovement.create(
            batch.getId(), MovementType.EXPIRED_DISPOSAL, command.quantity(), command.reason(), Instant.now(clock)
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
