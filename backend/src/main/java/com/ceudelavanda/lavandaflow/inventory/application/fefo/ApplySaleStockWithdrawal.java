package com.ceudelavanda.lavandaflow.inventory.application.fefo;

import com.ceudelavanda.lavandaflow.inventory.SaleStockWithdrawal;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemOperationLock;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemDetailsLookup;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.domain.StockQuantityRules;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InventoryItemNotFoundException;
import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Joins sales' transaction and reuses the existing FEFO writer without exposing inventory internals. */
@Service
@RequiredArgsConstructor
class ApplySaleStockWithdrawal implements SaleStockWithdrawal {
    private final InventoryItemOperationLock itemLock;
    private final InventoryItemDetailsLookup catalog;
    private final RegisterFefoWithdrawal fefo;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Result> withdraw(List<Line> lines) {
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("lines must not be empty");
        var seenItems = new HashSet<UUID>();
        var seenReferences = new HashSet<com.ceudelavanda.lavandaflow.inventory.StockAuditReference>();
        for (var line : lines) {
            Objects.requireNonNull(line, "line");
            Objects.requireNonNull(line.itemId(), "itemId");
            Objects.requireNonNull(line.reference(), "reference");
            StockQuantityRules.requirePositive(line.quantity(), "quantity");
            if (!seenItems.add(line.itemId()) || !seenReferences.add(line.reference()))
                throw new IllegalArgumentException("duplicate item or source reference");
        }
        var ordered = lines.stream().sorted(Comparator.comparing(Line::itemId)).toList();
        for (var line : ordered) itemLock.lockById(line.itemId())
            .orElseThrow(() -> new InventoryItemNotFoundException(line.itemId()));
        var details = catalog.findByIds(seenItems).stream().collect(java.util.stream.Collectors.toMap(i -> i.id(), i -> i));
        var results = new ArrayList<Result>();
        for (var line : ordered) {
            var item = details.get(line.itemId());
            if (item == null || !item.active() || !"FINISHED_PRODUCT".equals(item.category()) ||
                (item.unitOfMeasure() != UnitOfMeasure.UNIT && item.unitOfMeasure() != UnitOfMeasure.MILLILITER))
                throw new IneligibleSaleItemException("SALE_ITEM_INELIGIBLE", "Sale item is unavailable or ineligible", ErrorType.BUSINESS_RULE,
                    Map.of("inventoryItemId", line.itemId().toString()));
            var result = fefo.execute(new RegisterFefoWithdrawalCommand(line.itemId(), line.quantity(), null), line.reference());
            results.add(new Result(line.reference().referenceLineId(), item.name(), item.unitOfMeasure(), result.allocations().stream()
                .map(a -> new Allocation(a.batchId(), a.movementId(), a.quantity())).toList()));
        }
        return List.copyOf(results);
    }
    private static final class IneligibleSaleItemException extends DomainException {
        IneligibleSaleItemException(String code, String message, ErrorType type, Map<String, String> details) {
            super(code, message, type, details);
        }
    }
}
