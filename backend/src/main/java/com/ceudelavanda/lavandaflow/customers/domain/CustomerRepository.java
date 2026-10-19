package com.ceudelavanda.lavandaflow.customers.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Customer persistence boundary; no delete operation is exposed. */
public interface CustomerRepository {
    Customer save(Customer customer);
    Optional<Customer> findById(UUID id);
    List<Customer> findByIds(Collection<UUID> ids);

    /** Serializes contact edits and activation changes within the caller's write transaction. */
    Optional<Customer> findByIdForUpdate(UUID id);
}
