package com.ceudelavanda.lavandaflow.suppliers.application;

/** Filter and pagination input for supplier search. */
public record SupplierSearchQuery(
    String name,
    Boolean active,
    int page,
    int size
) {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public SupplierSearchQuery {
        name = normalizeName(name);
        if (page < 0) {
            throw new InvalidSupplierSearchQueryException("page", "must be zero or positive");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidSupplierSearchQueryException("size", "must be between 1 and " + MAX_SIZE);
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
