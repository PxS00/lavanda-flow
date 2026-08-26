package com.ceudelavanda.lavandaflow.inventory.application.query;

import com.ceudelavanda.lavandaflow.inventory.domain.exception.InvalidExpirationAlertWindowException;

/**
 * Input for retrieving expiration alerts within a resolved future alert window.
 *
 * <p>The window is inclusive and must be non-negative. A zero-day window still returns expired
 * batches, including batches whose expiration date is the evaluation date, but does not include
 * any future expiring-soon batch.</p>
 *
 * @param windowDays number of future days included in the alert horizon
 */
public record GetExpirationAlertsQuery(int windowDays) {

    public GetExpirationAlertsQuery {
        if (windowDays < 0) {
            throw new InvalidExpirationAlertWindowException(windowDays);
        }
    }
}
