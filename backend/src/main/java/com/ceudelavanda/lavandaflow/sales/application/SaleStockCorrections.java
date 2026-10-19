package com.ceudelavanda.lavandaflow.sales.application;

import com.ceudelavanda.lavandaflow.inventory.SaleStockReturn;
import com.ceudelavanda.lavandaflow.inventory.StockAuditReference;
import com.ceudelavanda.lavandaflow.sales.domain.*;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.util.*;

/** Coordinates authorization and inventory in one transaction without mutating confirmed sale snapshots. */
@Service
@RequiredArgsConstructor
public class SaleStockCorrections {
    private final OrderRepository orders;
    private final SaleStockCorrectionRepository corrections;
    private final SaleStockReturn inventory;

    /** Serializes retries and cumulative accepted returns on the sale row, then locks inventory item/batch. */
    @Transactional
    public SaleStockCorrection register(UUID saleId, UUID id, RegisterSaleStockCorrection request, String actor) {
        var order = orders.findByIdForUpdate(saleId).orElseThrow(() -> OrderException.notFound(saleId));
        requireConfirmed(order);
        if (id == null || request == null || request.lineId() == null || request.batchId() == null ||
            !Boolean.TRUE.equals(request.inspected()) || !Boolean.TRUE.equals(request.accepted()) ||
            request.reason() == null || request.reason().isBlank() || request.reason().trim().length() > 255 ||
            actor == null || actor.isBlank() || actor.length() > 100) throw invalid();
        var quantity = quantity(request.quantity());
        var reason = request.reason().trim();
        var previous = corrections.find(saleId, id);
        if (previous.isPresent()) {
            var saved = previous.get();
            if (!saved.lineId().equals(request.lineId()) || !saved.batchId().equals(request.batchId()) ||
                saved.quantity().compareTo(quantity) != 0 || !saved.reason().equals(reason))
                throw failure("SALE_CORRECTION_ID_CONFLICT", ErrorType.CONFLICT);
            return saved;
        }
        var line = order.lines().stream().filter(l -> l.id().equals(request.lineId())).findFirst()
            .orElseThrow(() -> failure("SALE_CORRECTION_ALLOCATION_INVALID", ErrorType.VALIDATION));
        var allocation = line.allocations().stream().filter(a -> a.batchId().equals(request.batchId())).findFirst()
            .orElseThrow(() -> failure("SALE_CORRECTION_ALLOCATION_INVALID", ErrorType.VALIDATION));
        var returned = corrections.findBySaleId(saleId).stream()
            .filter(c -> c.lineId().equals(line.id()) && c.batchId().equals(allocation.batchId()))
            .map(SaleStockCorrection::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (quantity.compareTo(allocation.quantity().subtract(returned)) > 0)
            throw failure("SALE_CORRECTION_LIMIT_EXCEEDED", ErrorType.CONFLICT);
        var movement = inventory.credit(line.itemId(), allocation.batchId(), quantity, reason,
            new StockAuditReference("SALE", saleId, line.id()));
        var result = new SaleStockCorrection(id, saleId, line.id(), allocation.batchId(), quantity,
            reason, true, true, actor, movement.occurredAt(), movement.movementId());
        corrections.insert(result);
        return result;
    }

    /** Reads persisted authorization and movement identities without consulting current eligibility. */
    @Transactional(readOnly = true)
    public List<SaleStockCorrection> list(UUID saleId) {
        var order = orders.findById(saleId).orElseThrow(() -> OrderException.notFound(saleId));
        requireConfirmed(order);
        return corrections.findBySaleId(saleId);
    }

    private static void requireConfirmed(Order order) {
        if (order.status() != OrderStatus.CONFIRMED) throw failure("SALE_CORRECTION_NOT_ALLOWED", ErrorType.CONFLICT);
    }
    private static BigDecimal quantity(BigDecimal value) {
        if (value == null || value.signum() <= 0 || (long) value.precision() - value.scale() > 13) throw invalid();
        try {
            var exact = value.setScale(6, RoundingMode.UNNECESSARY);
            if (exact.precision() > 19) throw invalid();
            return exact;
        } catch (ArithmeticException e) { throw invalid(); }
    }
    private static OrderException invalid() { return failure("INVALID_SALE_CORRECTION", ErrorType.VALIDATION); }
    private static OrderException failure(String code, ErrorType type) { return OrderException.correction(code, type); }
}
