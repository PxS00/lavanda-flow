package com.ceudelavanda.lavandaflow.customers.application;

/** Optional partial contact search and bounded, zero-based pagination. */
public record CustomerSearchQuery(String q, Boolean active, int page, int size) {
    public CustomerSearchQuery {
        q = q == null || q.trim().isEmpty() ? null : q.trim();
        if (page < 0) {
            throw new InvalidCustomerSearchQueryException("page", "must be zero or positive");
        }
        if (size < 1 || size > 100) {
            throw new InvalidCustomerSearchQueryException("size", "must be between 1 and 100");
        }
    }
}
