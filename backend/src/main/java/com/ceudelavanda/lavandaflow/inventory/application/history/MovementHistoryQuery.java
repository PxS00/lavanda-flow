package com.ceudelavanda.lavandaflow.inventory.application.history;

/** Read port for paginated, filtered stock-movement audit history. */
public interface MovementHistoryQuery {
    MovementHistoryPage find(GetMovementHistoryQuery query);
}
