package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.inventory.application.RegisterStockEntry;
import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterStockEntryCommand;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.request.RegisterStockEntryRequest;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response.RegisterStockEntryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final RegisterStockEntry registerStockEntry;

    @Operation(
        summary = "Register stock entry",
        description = "Adds a positive quantity to an existing batch and records the corresponding stock movement."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Stock entry registered successfully"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request data"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Batch not found"
        )
    })
    @PostMapping("/batches/{batchId}/entries")
    public ResponseEntity<RegisterStockEntryResponse> registerStockEntry(
        @PathVariable UUID batchId,
        @Valid @RequestBody RegisterStockEntryRequest request
    ) {
        var command = new RegisterStockEntryCommand(
            batchId,
            request.quantity(),
            request.reason()
        );

        var result = registerStockEntry.execute(command);
        var response = RegisterStockEntryResponse.from(result);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(response);
    }
}
