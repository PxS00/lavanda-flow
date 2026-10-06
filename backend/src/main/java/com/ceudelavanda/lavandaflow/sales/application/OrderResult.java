package com.ceudelavanda.lavandaflow.sales.application;

import com.ceudelavanda.lavandaflow.sales.domain.OrderStatus;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Draft display labels are live lookup values, never confirmation snapshots. Decimal values are exact. */
public record OrderResult(UUID id, UUID customerId, String customerName, OrderStatus status, String currency,
                          List<Line> lines, BigDecimal total, Instant createdAt, Instant updatedAt) {
    public record Line(UUID id, UUID itemId, String itemName, UnitOfMeasure unitOfMeasure,
                       BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {}
}
