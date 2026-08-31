package com.ceudelavanda.lavandaflow.inventory.application.history;

import java.util.List;

/** Paginated movement-history result exposed by the inventory application layer. */
public record MovementHistoryResult(
    List<MovementHistoryEntryResult> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public MovementHistoryResult {
        content = List.copyOf(content);
    }
}
