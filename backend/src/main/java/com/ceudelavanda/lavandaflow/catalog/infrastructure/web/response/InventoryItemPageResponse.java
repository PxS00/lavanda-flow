package com.ceudelavanda.lavandaflow.catalog.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.catalog.application.InventoryItemPage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated inventory catalog result")
public record InventoryItemPageResponse(
    List<InventoryItemResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public InventoryItemPageResponse {
        content = List.copyOf(content);
    }

    public static InventoryItemPageResponse from(InventoryItemPage result) {
        return new InventoryItemPageResponse(
            result.content().stream().map(InventoryItemResponse::from).toList(),
            result.page(),
            result.size(),
            result.totalElements(),
            result.totalPages()
        );
    }
}
