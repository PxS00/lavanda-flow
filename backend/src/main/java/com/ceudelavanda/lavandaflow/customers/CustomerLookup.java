package com.ceudelavanda.lavandaflow.customers;

import java.util.Optional;
import java.util.UUID;

/** Public read boundary for future sales consumers, without exposing customer persistence. */
public interface CustomerLookup {

    /**
     * Returns current values for either active or inactive contacts; missing IDs return empty.
     * Consumers must check active state for new commercial activity and snapshot values for history.
     * This read joins the caller transaction when present and never changes the contact.
     * @param customerId stable, non-null customer identity
     * @return immutable current values, or empty if the identity does not exist
     */
    Optional<CustomerSnapshot> findById(UUID customerId);
}
