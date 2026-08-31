package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.inventory.application.history.GetMovementHistory;
import com.ceudelavanda.lavandaflow.inventory.application.history.GetMovementHistoryQuery;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response.MovementHistoryResponse;
import com.ceudelavanda.lavandaflow.shared.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory/movements")
public class MovementHistoryController {

    private final GetMovementHistory getMovementHistory;

    @Operation(
        summary = "Retrieve inventory movement history",
        description = "Returns immutable stock movements newest first. Filters are optional, "
            + "the from instant is inclusive, the to instant is exclusive, and ordering is fixed "
            + "by occurredAt descending with movementId as a deterministic tie-breaker."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Movement history retrieved successfully", content = @Content(schema = @Schema(implementation = MovementHistoryResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid filter or pagination parameters", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<MovementHistoryResponse> getMovementHistory(
        @Parameter(description = "Optional inventory item identifier") @RequestParam(required = false) UUID inventoryItemId,
        @Parameter(description = "Optional inventory batch identifier") @RequestParam(required = false) UUID batchId,
        @Parameter(description = "Optional exact movement type") @RequestParam(required = false) MovementType type,
        @Parameter(description = "Optional inclusive ISO-8601 lower occurrence instant")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @Parameter(description = "Optional exclusive ISO-8601 upper occurrence instant")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @Parameter(description = "Zero-based page number", schema = @Schema(defaultValue = "0", minimum = "0"))
        @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Page size from 1 to 100", schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
        @RequestParam(defaultValue = "20") int size
    ) {
        var result = getMovementHistory.execute(new GetMovementHistoryQuery(
            inventoryItemId, batchId, type, from, to, page, size
        ));
        return ResponseEntity.ok(MovementHistoryResponse.from(result));
    }
}
