package com.ceudelavanda.lavandaflow.production.application.execution;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.production.domain.FormulaIngredient;
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormula;
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormulaKind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionRequirementCalculatorTest {

    private final ProductionRequirementCalculator calculator = new ProductionRequirementCalculator();

    @Test
    void shouldScaleMixedUnitRequirementsExactly() {
        var bulkId = UUID.randomUUID();
        var bottleId = UUID.randomUUID();
        var formula = ProductionFormula.create(
            ProductionFormulaKind.PACKAGED_FILLING,
            UUID.randomUUID(),
            BigDecimal.ONE,
            UnitOfMeasure.UNIT,
            List.of(
                new FormulaIngredient(bulkId, new BigDecimal("30"), UnitOfMeasure.MILLILITER),
                new FormulaIngredient(bottleId, BigDecimal.ONE, UnitOfMeasure.UNIT)
            )
        );

        var requirements = calculator.calculate(formula, new BigDecimal("5"));

        assertThat(requirements).containsExactly(
            new ProductionRequirementCalculator.Requirement(bulkId, new BigDecimal("150.000000"), UnitOfMeasure.MILLILITER),
            new ProductionRequirementCalculator.Requirement(bottleId, new BigDecimal("5.000000"), UnitOfMeasure.UNIT)
        );
    }

    @Test
    void shouldRejectRequirementThatNeedsSilentRounding() {
        var ingredientId = UUID.randomUUID();
        var formula = ProductionFormula.create(
            UUID.randomUUID(),
            new BigDecimal("3"),
            UnitOfMeasure.UNIT,
            List.of(new FormulaIngredient(ingredientId, BigDecimal.ONE, UnitOfMeasure.UNIT))
        );

        assertThatThrownBy(() -> calculator.calculate(formula, BigDecimal.ONE))
            .isInstanceOf(UnrepresentableProductionRequirementException.class);
    }
}
