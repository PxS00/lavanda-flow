package com.ceudelavanda.lavandaflow.sales.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Sales-owned aggregate. Draft saves do not reserve stock or guarantee availability. */
public record Order(UUID id, UUID customerId, OrderStatus status, List<OrderLine> lines,
                    BigDecimal total, Instant createdAt, Instant updatedAt, String customerName, String customerPhone, String customerEmail, Instant confirmedAt) {
    public Order(UUID id, UUID customerId, OrderStatus status, List<OrderLine> lines, BigDecimal total, Instant createdAt, Instant updatedAt) {
        this(id, customerId, status, lines, total, createdAt, updatedAt, null, null, null, null);
    }
    public Order { lines = List.copyOf(lines); }
}
