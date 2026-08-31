package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.inventory.application.history.MovementHistoryResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated immutable inventory movement history")
public record MovementHistoryResponse(
    List<MovementHistoryEntryResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public MovementHistoryResponse {
        content = List.copyOf(content);
    }

    public static MovementHistoryResponse from(MovementHistoryResult result) {
        return new MovementHistoryResponse(
            result.content().stream().map(MovementHistoryEntryResponse::from).toList(),
            result.page(), result.size(), result.totalElements(), result.totalPages()
        );
    }
}
