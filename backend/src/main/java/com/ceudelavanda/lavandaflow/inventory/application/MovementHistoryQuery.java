package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.inventory.application.query.GetMovementHistoryQuery;

/**
 * Read port for paginated, filtered stock-movement audit history.
 *
 * <p>Implementations must preserve newest-first deterministic ordering by occurrence instant
 * and movement identifier.</p>
 */
public interface MovementHistoryQuery {

    MovementHistoryPage find(GetMovementHistoryQuery query);
}
