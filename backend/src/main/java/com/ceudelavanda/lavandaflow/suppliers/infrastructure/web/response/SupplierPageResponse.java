package com.ceudelavanda.lavandaflow.suppliers.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.suppliers.application.SupplierPage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated supplier result")
public record SupplierPageResponse(
    List<SupplierResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public SupplierPageResponse {
        content = List.copyOf(content);
    }

    public static SupplierPageResponse from(SupplierPage result) {
        return new SupplierPageResponse(
            result.content().stream().map(SupplierResponse::from).toList(),
            result.page(),
            result.size(),
            result.totalElements(),
            result.totalPages()
        );
    }
}
