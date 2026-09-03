package com.ceudelavanda.lavandaflow.production.infrastructure.web;

import com.ceudelavanda.lavandaflow.production.application.formula.CreateProductionFormula;
import com.ceudelavanda.lavandaflow.production.application.formula.GetProductionFormula;
import com.ceudelavanda.lavandaflow.production.application.formula.ListProductionFormulas;
import com.ceudelavanda.lavandaflow.production.application.formula.ProductionFormulaDefinitionCommand;
import com.ceudelavanda.lavandaflow.production.application.formula.ProductionFormulaIngredientCommand;
import com.ceudelavanda.lavandaflow.production.application.formula.UpdateProductionFormula;
import com.ceudelavanda.lavandaflow.production.infrastructure.web.request.UpsertProductionFormulaRequest;
import com.ceudelavanda.lavandaflow.production.infrastructure.web.response.ProductionFormulaResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/production/formulas")
public class ProductionFormulaController {

    private final CreateProductionFormula createProductionFormula;
    private final UpdateProductionFormula updateProductionFormula;
    private final GetProductionFormula getProductionFormula;
    private final ListProductionFormulas listProductionFormulas;

    @Operation(summary = "Create a production formula")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Production formula created", content = @Content(schema = @Schema(implementation = ProductionFormulaResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid formula definition", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Referenced catalog item not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Referenced catalog item is inactive", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ProductionFormulaResponse> create(@Valid @RequestBody UpsertProductionFormulaRequest request) {
        var result = createProductionFormula.execute(toCommand(request));
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .location(URI.create("/api/v1/production/formulas/" + result.id()))
            .body(ProductionFormulaResponse.from(result));
    }

    @Operation(summary = "Replace a production formula definition")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Production formula updated", content = @Content(schema = @Schema(implementation = ProductionFormulaResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid formula definition", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Formula or referenced catalog item not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Referenced catalog item is inactive", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/{formulaId}")
    public ResponseEntity<ProductionFormulaResponse> update(
        @PathVariable UUID formulaId,
        @Valid @RequestBody UpsertProductionFormulaRequest request
    ) {
        return ResponseEntity.ok(ProductionFormulaResponse.from(
            updateProductionFormula.execute(formulaId, toCommand(request))
        ));
    }

    @Operation(summary = "Retrieve a production formula")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Production formula retrieved", content = @Content(schema = @Schema(implementation = ProductionFormulaResponse.class))),
        @ApiResponse(responseCode = "404", description = "Production formula not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{formulaId}")
    public ResponseEntity<ProductionFormulaResponse> getById(@PathVariable UUID formulaId) {
        return ResponseEntity.ok(ProductionFormulaResponse.from(getProductionFormula.execute(formulaId)));
    }

    @Operation(summary = "List production formulas")
    @GetMapping
    public ResponseEntity<List<ProductionFormulaResponse>> list() {
        return ResponseEntity.ok(listProductionFormulas.execute().stream()
            .map(ProductionFormulaResponse::from)
            .toList());
    }

    private static ProductionFormulaDefinitionCommand toCommand(UpsertProductionFormulaRequest request) {
        return new ProductionFormulaDefinitionCommand(
            request.outputInventoryItemId(),
            request.outputQuantity(),
            request.ingredients().stream()
                .map(ingredient -> new ProductionFormulaIngredientCommand(
                    ingredient.inventoryItemId(),
                    ingredient.quantity()
                ))
                .toList()
        );
    }
}
