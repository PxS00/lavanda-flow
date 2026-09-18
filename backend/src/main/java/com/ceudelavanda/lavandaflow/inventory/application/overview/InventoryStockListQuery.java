package com.ceudelavanda.lavandaflow.inventory.application.overview;

import java.util.LinkedHashSet;
import java.util.List;

/** Validated filters for paginated operational stock browsing. */
public record InventoryStockListQuery(List<String> categories, int page, int size, int expirationWindowDays) {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public InventoryStockListQuery {
        categories = categories == null ? List.of() : List.copyOf(new LinkedHashSet<>(categories));
        if (page < 0) {
            throw new InvalidInventoryStockListQueryException("page", "must be zero or positive");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidInventoryStockListQueryException("size", "must be between 1 and " + MAX_SIZE);
        }
        if (expirationWindowDays < 0) {
            throw new InvalidInventoryStockListQueryException("expirationWindowDays", "must be zero or positive");
        }
    }
}
