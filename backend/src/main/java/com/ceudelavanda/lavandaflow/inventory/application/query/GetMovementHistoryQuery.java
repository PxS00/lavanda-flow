package com.ceudelavanda.lavandaflow.inventory.application.query;

import com.ceudelavanda.lavandaflow.inventory.application.exception.InvalidMovementHistoryQueryException;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;

import java.time.Instant;
import java.util.UUID;

/**
 * Filter and pagination input for the immutable movement-history read model.
 *
 * <p>The lower temporal bound is inclusive and the upper bound is exclusive.
 * Pagination is zero-based and intentionally capped to keep the audit endpoint bounded.</p>
 */
public record GetMovementHistoryQuery(
    UUID inventoryItemId,
    UUID batchId,
    MovementType type,
    Instant from,
    Instant to,
    int page,
    int size
) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public GetMovementHistoryQuery {
        if (page < 0) {
            throw new InvalidMovementHistoryQueryException(
                "page",
                "must be zero or positive"
            );
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidMovementHistoryQueryException(
                "size",
                "must be between 1 and " + MAX_SIZE
            );
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new InvalidMovementHistoryQueryException(
                "dateRange",
                "from must be earlier than to"
            );
        }
    }
}
