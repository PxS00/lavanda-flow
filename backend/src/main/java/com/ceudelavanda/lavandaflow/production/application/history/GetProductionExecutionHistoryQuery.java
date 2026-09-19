package com.ceudelavanda.lavandaflow.production.application.history;

import java.time.LocalDate;

/** Date filters and pagination for completed production history. */
public record GetProductionExecutionHistoryQuery(
    LocalDate from,
    LocalDate to,
    int page,
    int size
) {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public GetProductionExecutionHistoryQuery {
        if (page < 0) {
            throw new InvalidProductionExecutionHistoryQueryException("page", "must be zero or positive");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidProductionExecutionHistoryQueryException(
                "size", "must be between 1 and " + MAX_SIZE
            );
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidProductionExecutionHistoryQueryException(
                "dateRange", "from must be earlier than or equal to to"
            );
        }
    }
}
