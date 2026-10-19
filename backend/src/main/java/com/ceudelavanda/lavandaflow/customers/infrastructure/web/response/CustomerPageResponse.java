package com.ceudelavanda.lavandaflow.customers.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.customers.application.CustomerPage;
import java.util.List;

/** Standard zero-based paginated HTTP contact result. */
public record CustomerPageResponse(List<CustomerResponse> content, int page, int size, long totalElements, int totalPages) {
    public static CustomerPageResponse from(CustomerPage result) {
        return new CustomerPageResponse(result.content().stream().map(CustomerResponse::from).toList(),
            result.page(), result.size(), result.totalElements(), result.totalPages());
    }
}
