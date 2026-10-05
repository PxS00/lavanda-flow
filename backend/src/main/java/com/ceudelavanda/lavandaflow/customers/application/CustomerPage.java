package com.ceudelavanda.lavandaflow.customers.application;

import java.util.List;

/** Framework-neutral immutable page, ordered by name and then stable identity. */
public record CustomerPage(List<CustomerResult> content, int page, int size, long totalElements, int totalPages) {
    public CustomerPage {
        content = List.copyOf(content);
    }
}
