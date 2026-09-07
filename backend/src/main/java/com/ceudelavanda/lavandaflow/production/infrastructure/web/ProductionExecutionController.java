package com.ceudelavanda.lavandaflow.production.infrastructure.web;

import com.ceudelavanda.lavandaflow.production.application.execution.ProductionSourceAllocationCommand;
import com.ceudelavanda.lavandaflow.production.application.execution.RegisterProduction;
import com.ceudelavanda.lavandaflow.production.application.execution.RegisterProductionCommand;
import com.ceudelavanda.lavandaflow.production.infrastructure.web.request.RegisterProductionRequest;
import com.ceudelavanda.lavandaflow.production.infrastructure.web.response.ProductionExecutionResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/production/executions")
public class ProductionExecutionController {

    private final RegisterProduction registerProduction;

    @Operation(
        summary = "Register a completed production execution",
        description = "Validates exact source batches against the scaled formula, resolves the definitive internal lot code, applies inventory effects, and persists production traceability in one local transaction."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Production execution registered", content = @Content(schema = @Schema(implementation = ProductionExecutionResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid quantity, dates, or lot-code input", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Formula, catalog item, or source batch not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Formula allocation mismatch, inactive or incompatible catalog state, exhausted generated lot sequence, or ineligible/insufficient inventory stock", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ProductionExecutionResponse> register(
        @Valid @RequestBody RegisterProductionRequest request
    ) {
        var result = registerProduction.execute(new RegisterProductionCommand(
            request.formulaId(),
            request.outputQuantity(),
            request.sourceAllocations().stream()
                .map(allocation -> new ProductionSourceAllocationCommand(
                    allocation.batchId(),
                    allocation.quantity()
                ))
                .toList(),
            request.productionDate(),
            request.outputReceivedAt(),
            request.outputExpiresAt(),
            request.lotCodeMode(),
            request.manualLotCode()
        ));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ProductionExecutionResponse.from(result));
    }
}
