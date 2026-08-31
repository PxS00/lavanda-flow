package com.ceudelavanda.lavandaflow.inventory.application.alerts;

import com.ceudelavanda.lavandaflow.inventory.domain.exception.InvalidExpirationAlertWindowException;

/** Input for retrieving expiration alerts within a resolved future alert window. */
public record GetExpirationAlertsQuery(int windowDays) {
    public GetExpirationAlertsQuery {
        if (windowDays < 0) {
            throw new InvalidExpirationAlertWindowException(windowDays);
        }
    }
}
