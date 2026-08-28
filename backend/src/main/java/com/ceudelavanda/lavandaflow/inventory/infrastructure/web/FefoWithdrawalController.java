package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.inventory.application.fefo.RegisterFefoWithdrawal;
import com.ceudelavanda.lavandaflow.inventory.application.fefo.RegisterFefoWithdrawalCommand;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.request.RegisterFefoWithdrawalRequest;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response.RegisterFefoWithdrawalResponse;
import com.ceudelavanda.lavandaflow.shared.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory")
public class FefoWithdrawalController {

    private final RegisterFefoWithdrawal registerFefoWithdrawal;

    @Operation(
        summary = "Register automatic FEFO stock withdrawal",
        description = "Automatically allocates a withdrawal across eligible batches using FEFO. "
            + "Batches expiring today are considered expired. Multiple batches may be consumed, "
            + "and no partial withdrawal is committed when eligible stock is insufficient."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "FEFO stock withdrawal registered successfully", content = @Content(schema = @Schema(implementation = RegisterFefoWithdrawalResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Inventory item not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Inventory item is inactive or eligible stock is insufficient", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/items/{inventoryItemId}/withdrawals")
    public ResponseEntity<RegisterFefoWithdrawalResponse> registerFefoWithdrawal(
        @PathVariable UUID inventoryItemId,
        @Valid @RequestBody RegisterFefoWithdrawalRequest request
    ) {
        var result = registerFefoWithdrawal.execute(
            new RegisterFefoWithdrawalCommand(inventoryItemId, request.quantity(), request.reason())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterFefoWithdrawalResponse.from(result));
    }
}
