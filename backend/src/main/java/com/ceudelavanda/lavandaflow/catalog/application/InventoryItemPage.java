package com.ceudelavanda.lavandaflow.catalog.application;

import java.util.List;

/** Framework-neutral page of inventory catalog items. */
public record InventoryItemPage(
    List<InventoryItemResult> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public InventoryItemPage {
        content = List.copyOf(content);
    }
}
