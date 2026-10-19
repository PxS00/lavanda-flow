package com.ceudelavanda.lavandaflow.sales.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Stable draft identity and exact entered values; display snapshots belong to confirmation. */
public record OrderLine(UUID id, UUID itemId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount,
    String itemName, com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure unitOfMeasure,
    java.util.List<SaleAllocation> allocations) {
    public OrderLine { allocations = java.util.List.copyOf(allocations); }
    public OrderLine(UUID id, UUID itemId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {
        this(id, itemId, quantity, unitPrice, amount, null, null, java.util.List.of());
    }
}
