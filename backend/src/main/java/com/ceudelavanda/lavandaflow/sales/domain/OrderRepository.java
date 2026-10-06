package com.ceudelavanda.lavandaflow.sales.domain;

import java.util.Optional;
import java.util.UUID;

/** Persistence boundary; edits serialize on the aggregate row in the caller transaction. */
public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(UUID id);
    Optional<Order> findByIdForUpdate(UUID id);
}
