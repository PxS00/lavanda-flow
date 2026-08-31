package com.ceudelavanda.lavandaflow.inventory.application.receipt;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemLookup;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovement;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InactiveInventoryItemException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InactiveSupplierException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InventoryItemNotFoundException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.SupplierNotFoundException;
import com.ceudelavanda.lavandaflow.suppliers.SupplierLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Registers one business stock receipt as a new batch and one immutable initial ENTRY movement.
 *
 * <p>Catalog and supplier references are validated through published module contracts. Batch persistence
 * and movement persistence are deliberately orchestrated here, under one transaction, so neither side
 * effect can commit without the other.</p>
 */
@Service
@RequiredArgsConstructor
public class RegisterStockReceipt {

    private final InventoryItemLookup inventoryItemLookup;
    private final SupplierLookup supplierLookup;
    private final BatchRepository batchRepository;
    private final StockMovementRepository stockMovementRepository;
    private final Clock clock;

    /**
     * Creates a new batch and its initial stock audit movement atomically.
     *
     * @param command receipt data
     * @return committed operational receipt data
     * @throws InventoryItemNotFoundException if the catalog item does not exist
     * @throws InactiveInventoryItemException if the catalog item is inactive
     * @throws SupplierNotFoundException if a supplied supplier reference does not exist
     * @throws InactiveSupplierException if a supplied supplier is inactive
     */
    @Transactional
    public StockReceiptResult execute(RegisterStockReceiptCommand command) {
        var inventoryItem = inventoryItemLookup.findById(command.inventoryItemId())
            .orElseThrow(() -> new InventoryItemNotFoundException(command.inventoryItemId()));

        if (!inventoryItem.active()) {
            throw new InactiveInventoryItemException(command.inventoryItemId());
        }

        validateSupplier(command.supplierId());

        var batch = Batch.create(
            command.inventoryItemId(),
            command.supplierId(),
            command.lotCode(),
            command.quantity(),
            command.receivedAt(),
            command.expiresAt()
        );
        var occurredAt = Instant.now(clock);
        var movement = StockMovement.create(
            batch.getId(),
            MovementType.ENTRY,
            command.quantity(),
            command.reason(),
            occurredAt
        );

        batchRepository.save(batch);
        stockMovementRepository.save(movement);

        return new StockReceiptResult(
            batch.getId(),
            movement.id(),
            batch.getInventoryItemId(),
            batch.getSupplierId(),
            batch.getLotCode(),
            batch.getInitialQuantity(),
            batch.getReceivedAt(),
            batch.getExpiresAt(),
            movement.reason(),
            movement.occurredAt()
        );
    }

    private void validateSupplier(UUID supplierId) {
        if (supplierId == null) {
            return;
        }

        var supplier = supplierLookup.findById(supplierId)
            .orElseThrow(() -> new SupplierNotFoundException(supplierId));

        if (!supplier.active()) {
            throw new InactiveSupplierException(supplierId);
        }
    }
}
