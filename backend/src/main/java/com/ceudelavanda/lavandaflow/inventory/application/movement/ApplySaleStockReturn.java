package com.ceudelavanda.lavandaflow.inventory.application.movement;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemOperationLock;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemDetailsLookup;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.SaleStockReturn;
import com.ceudelavanda.lavandaflow.inventory.StockAuditReference;
import com.ceudelavanda.lavandaflow.inventory.domain.*;
import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
class ApplySaleStockReturn implements SaleStockReturn {
    private final InventoryItemOperationLock itemLock;
    private final InventoryItemDetailsLookup catalog;
    private final BatchRepository batches;
    private final StockMovementRepository movements;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Result credit(UUID itemId, UUID batchId, BigDecimal quantity, String reason, StockAuditReference reference) {
        StockQuantityRules.requirePositive(quantity, "quantity");
        Objects.requireNonNull(reference, "reference");
        if (reason == null || reason.isBlank() || reason.trim().length() > 255)
            throw new IllegalArgumentException("reason must contain 1 to 255 characters");
        itemLock.lockById(itemId).orElseThrow(IneligibleReturn::new);
        var item = catalog.findByIds(List.of(itemId)).stream().findFirst().orElseThrow(IneligibleReturn::new);
        batches.lockByIdForUpdate(batchId);
        var batch = batches.findById(batchId).orElseThrow(IneligibleReturn::new);
        if (!item.active() || !"FINISHED_PRODUCT".equals(item.category()) ||
            (item.unitOfMeasure() != UnitOfMeasure.UNIT && item.unitOfMeasure() != UnitOfMeasure.MILLILITER) ||
            !itemId.equals(batch.getInventoryItemId()) || batch.getExpiresAt() == null ||
            !batch.getExpiresAt().isAfter(LocalDate.now(clock))) throw new IneligibleReturn();
        batch.addQuantity(quantity);
        var movement = new StockMovement(UUID.randomUUID(), batchId, MovementType.ADJUSTMENT_IN,
            quantity, reason, Instant.now(clock).truncatedTo(ChronoUnit.MICROS), reference);
        batches.save(batch);
        movements.save(movement);
        return new Result(movement.id(), movement.occurredAt());
    }

    private static final class IneligibleReturn extends DomainException {
        IneligibleReturn() {
            super("SALE_RETURN_INELIGIBLE", "Original batch is not eligible for saleable returns", ErrorType.BUSINESS_RULE, null);
        }
    }
}
