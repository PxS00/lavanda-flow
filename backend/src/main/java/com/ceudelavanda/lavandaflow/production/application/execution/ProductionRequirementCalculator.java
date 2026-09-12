package com.ceudelavanda.lavandaflow.production.application.execution;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormula;
import com.ceudelavanda.lavandaflow.production.domain.exception.InvalidProductionExecutionException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/** Calculates exact scaled formula requirements without reserving stock or production state. */
@Component
public class ProductionRequirementCalculator {

    public List<Requirement> calculate(ProductionFormula formula, BigDecimal requestedOutputQuantity) {
        if (formula == null) {
            throw new InvalidProductionExecutionException("formula", "Production formula must not be null");
        }
        requireSupportedPositiveQuantity(requestedOutputQuantity, "outputQuantity");
        return formula.getIngredients().stream()
            .map(ingredient -> new Requirement(
                ingredient.inventoryItemId(),
                scale(
                    ingredient.quantity(),
                    requestedOutputQuantity,
                    formula.getOutputQuantity(),
                    ingredient.inventoryItemId()
                ),
                ingredient.unitOfMeasure()
            ))
            .toList();
    }

    private BigDecimal scale(
        BigDecimal ingredientQuantity,
        BigDecimal requestedOutputQuantity,
        BigDecimal referenceOutputQuantity,
        UUID inventoryItemId
    ) {
        try {
            var scaled = ingredientQuantity
                .multiply(requestedOutputQuantity)
                .divide(referenceOutputQuantity, 6, RoundingMode.UNNECESSARY);
            var integerDigits = Math.max(scaled.precision() - scaled.scale(), 0);
            if (integerDigits > 13 || scaled.signum() <= 0) {
                throw new UnrepresentableProductionRequirementException(inventoryItemId);
            }
            return scaled;
        } catch (ArithmeticException exception) {
            throw new UnrepresentableProductionRequirementException(inventoryItemId);
        }
    }

    private void requireSupportedPositiveQuantity(BigDecimal quantity, String field) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new InvalidProductionExecutionException(field, "Quantity must be greater than zero");
        }
        var fractionDigits = Math.max(quantity.scale(), 0);
        var integerDigits = Math.max(quantity.precision() - quantity.scale(), 0);
        if (integerDigits > 13 || fractionDigits > 6) {
            throw new InvalidProductionExecutionException(
                field,
                "Quantity must have at most 13 integer digits and 6 fractional digits"
            );
        }
    }

    public record Requirement(
        UUID inventoryItemId,
        BigDecimal quantity,
        UnitOfMeasure unitOfMeasure
    ) {
    }
}
