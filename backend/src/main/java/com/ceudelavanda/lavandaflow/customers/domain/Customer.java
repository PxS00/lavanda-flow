package com.ceudelavanda.lavandaflow.customers.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Stable customer identity and audit times; maintenance never deletes or replaces identity. */
public record Customer(UUID id, CustomerContact contact, boolean active, Instant createdAt, Instant updatedAt) {
    public Customer {
        Objects.requireNonNull(id);
        Objects.requireNonNull(contact);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    public static Customer create(CustomerContact contact, Instant now) {
        return new Customer(UUID.randomUUID(), contact, true, now, now);
    }

    public Customer update(CustomerContact contact, Instant now) {
        return new Customer(id, contact, active, createdAt, now);
    }

    public Customer changeActiveState(boolean active, Instant now) {
        return new Customer(id, contact, active, createdAt, now);
    }
}
