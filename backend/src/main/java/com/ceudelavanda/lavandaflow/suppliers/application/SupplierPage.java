package com.ceudelavanda.lavandaflow.suppliers.application;

import java.util.List;

/** Framework-neutral page of suppliers. */
public record SupplierPage(
    List<SupplierResult> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public SupplierPage {
        content = List.copyOf(content);
    }
}
