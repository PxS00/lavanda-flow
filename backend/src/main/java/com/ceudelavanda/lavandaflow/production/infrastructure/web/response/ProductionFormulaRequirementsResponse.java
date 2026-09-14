package com.ceudelavanda.lavandaflow.production.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.production.application.formula.ProductionFormulaRequirementsResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Backend-calculated scaled requirements for a requested production output quantity")
public record ProductionFormulaRequirementsResponse(
    UUID formulaId,
    UUID outputInventoryItemId,
    String outputQuantity,
    UnitOfMeasure outputUnitOfMeasure,
    List<RequirementResponse> requirements
) {

    public ProductionFormulaRequirementsResponse {
        requirements = List.copyOf(requirements);
    }

    public static ProductionFormulaRequirementsResponse from(ProductionFormulaRequirementsResult result) {
        return new ProductionFormulaRequirementsResponse(
            result.formulaId(),
            result.outputInventoryItemId(),
            result.outputQuantity().toPlainString(),
            result.outputUnitOfMeasure(),
            result.requirements().stream()
                .map(requirement -> new RequirementResponse(
                    requirement.inventoryItemId(),
                    requirement.quantity().toPlainString(),
                    requirement.unitOfMeasure()
                ))
                .toList()
        );
    }

    public record RequirementResponse(
        UUID inventoryItemId,
        String quantity,
        UnitOfMeasure unitOfMeasure
    ) {
    }
}
