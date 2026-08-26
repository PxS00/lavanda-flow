package com.ceudelavanda.lavandaflow.inventory.application.result;

/**
 * Expiration classification exposed by the inventory alert read model.
 *
 * <p>{@link #EXPIRED} includes batches expiring on the evaluation date. {@link #EXPIRING_SOON}
 * applies only to future expiration dates inside the configured alert window.</p>
 */
public enum ExpirationAlertStatus {
    EXPIRED,
    EXPIRING_SOON
}
