package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.catalog.domain.Category;

/** Filter and pagination input for inventory-item search. */
public record InventoryItemSearchQuery(
    String name,
    Category category,
    Boolean active,
    int page,
    int size
) {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public InventoryItemSearchQuery {
        name = normalizeName(name);
        if (page < 0) {
            throw new InvalidInventoryItemSearchQueryException("page", "must be zero or positive");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidInventoryItemSearchQueryException("size", "must be between 1 and " + MAX_SIZE);
        }
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        var normalized = name.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
