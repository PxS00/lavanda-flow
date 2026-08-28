package com.ceudelavanda.lavandaflow.inventory.application.history;

import java.util.List;

/** Framework-neutral page returned by the movement-history read port. */
public record MovementHistoryPage(
    List<MovementHistoryEntry> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public MovementHistoryPage {
        content = List.copyOf(content);
    }
}
