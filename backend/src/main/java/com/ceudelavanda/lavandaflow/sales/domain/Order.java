package com.ceudelavanda.lavandaflow.sales.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Sales-owned aggregate. Draft saves do not reserve stock or guarantee availability. */
public record Order(UUID id, UUID customerId, OrderStatus status, List<OrderLine> lines,
                    BigDecimal total, Instant createdAt, Instant updatedAt) {
    public Order { lines = List.copyOf(lines); }
}
