package com.ceudelavanda.lavandaflow.production.application.formula;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.production.application.execution.ProductionRequirementCalculator;
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormula;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductionFormulaRequirementsResult(
    UUID formulaId,
    UUID outputInventoryItemId,
    BigDecimal outputQuantity,
    UnitOfMeasure outputUnitOfMeasure,
    List<RequirementResult> requirements
) {

    public ProductionFormulaRequirementsResult {
        requirements = List.copyOf(requirements);
    }

    static ProductionFormulaRequirementsResult from(
        ProductionFormula formula,
        BigDecimal outputQuantity,
        List<ProductionRequirementCalculator.Requirement> requirements
    ) {
        return new ProductionFormulaRequirementsResult(
            formula.getId(),
            formula.getOutputInventoryItemId(),
            outputQuantity,
            formula.getOutputUnitOfMeasure(),
            requirements.stream()
                .map(requirement -> new RequirementResult(
                    requirement.inventoryItemId(),
                    requirement.quantity(),
                    requirement.unitOfMeasure()
                ))
                .toList()
        );
    }

    public record RequirementResult(
        UUID inventoryItemId,
        BigDecimal quantity,
        UnitOfMeasure unitOfMeasure
    ) {
    }
}
