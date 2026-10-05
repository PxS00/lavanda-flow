package com.ceudelavanda.lavandaflow.customers.application;

import com.ceudelavanda.lavandaflow.customers.domain.Customer;
import java.time.Instant;
import java.util.UUID;

/** Immutable application result including application-clock audit timestamps. */
public record CustomerResult(UUID id, String name, String phone, String email, boolean active,
                             Instant createdAt, Instant updatedAt) {
    public static CustomerResult from(Customer customer) {
        return new CustomerResult(customer.id(), customer.contact().name(), customer.contact().phone(),
            customer.contact().email(), customer.active(), customer.createdAt(), customer.updatedAt());
    }
}
