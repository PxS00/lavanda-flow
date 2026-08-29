package com.ceudelavanda.lavandaflow.inventory.application.batch;

/**
 * Operational state derived from a batch balance and expiration date.
 */
public enum BatchOperationalStatus {
    AVAILABLE,
    EXPIRED,
    ZERO_BALANCE
}
