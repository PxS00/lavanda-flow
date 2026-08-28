package com.ceudelavanda.lavandaflow.catalog.infrastructure.web;

import com.ceudelavanda.lavandaflow.catalog.application.GetInventoryItem;
import com.ceudelavanda.lavandaflow.catalog.application.InventoryItemSearchQuery;
import com.ceudelavanda.lavandaflow.catalog.application.RegisterInventoryItem;
import com.ceudelavanda.lavandaflow.catalog.application.RegisterInventoryItemCommand;
import com.ceudelavanda.lavandaflow.catalog.application.SearchInventoryItems;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.infrastructure.web.request.RegisterInventoryItemRequest;
import com.ceudelavanda.lavandaflow.catalog.infrastructure.web.response.InventoryItemPageResponse;
import com.ceudelavanda.lavandaflow.catalog.infrastructure.web.response.InventoryItemResponse;
import com.ceudelavanda.lavandaflow.shared.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory-items")
public class InventoryItemController {

    private final RegisterInventoryItem registerInventoryItem;
    private final GetInventoryItem getInventoryItem;
    private final SearchInventoryItems searchInventoryItems;

    @Operation(summary = "Register an inventory catalog item")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Inventory item registered", content = @Content(schema = @Schema(implementation = InventoryItemResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<InventoryItemResponse> register(@Valid @RequestBody RegisterInventoryItemRequest request) {
        var result = registerInventoryItem.execute(new RegisterInventoryItemCommand(
            request.name(),
            request.description(),
            request.category(),
            request.unitOfMeasure()
        ));
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .location(URI.create("/api/v1/inventory-items/" + result.id()))
            .body(InventoryItemResponse.from(result));
    }

    @Operation(summary = "Retrieve an inventory catalog item")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Inventory item retrieved", content = @Content(schema = @Schema(implementation = InventoryItemResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid inventory item identifier", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Inventory item not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{inventoryItemId}")
    public ResponseEntity<InventoryItemResponse> getById(@PathVariable UUID inventoryItemId) {
        return ResponseEntity.ok(InventoryItemResponse.from(getInventoryItem.execute(inventoryItemId)));
    }

    @Operation(
        summary = "Search inventory catalog items",
        description = "Filters are optional. Name matching is partial and case-insensitive. Results are ordered by name and then identifier."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Inventory items retrieved", content = @Content(schema = @Schema(implementation = InventoryItemPageResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid filters or pagination", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<InventoryItemPageResponse> search(
        @Parameter(description = "Optional partial, case-insensitive name filter")
        @RequestParam(required = false) String name,
        @Parameter(description = "Optional exact category filter")
        @RequestParam(required = false) Category category,
        @Parameter(description = "Optional active-state filter")
        @RequestParam(required = false) Boolean active,
        @Parameter(description = "Zero-based page number", schema = @Schema(defaultValue = "0", minimum = "0"))
        @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Page size from 1 to 100", schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
        @RequestParam(defaultValue = "20") int size
    ) {
        var result = searchInventoryItems.execute(new InventoryItemSearchQuery(name, category, active, page, size));
        return ResponseEntity.ok(InventoryItemPageResponse.from(result));
    }
}
