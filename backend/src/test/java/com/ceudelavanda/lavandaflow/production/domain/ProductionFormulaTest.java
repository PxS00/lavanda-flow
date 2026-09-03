package com.ceudelavanda.lavandaflow.production.domain;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.production.domain.exception.InvalidProductionFormulaException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionFormulaTest {

    @Test
    void shouldCreateFormulaWithDefensiveIngredientDefinition() {
        var outputItemId = UUID.randomUUID();
        var ingredient = new FormulaIngredient(
            UUID.randomUUID(), new BigDecimal("250.125000"), UnitOfMeasure.MILLILITER
        );
        var ingredients = new ArrayList<>(List.of(ingredient));

        var formula = ProductionFormula.create(
            outputItemId,
            new BigDecimal("1000.000000"),
            UnitOfMeasure.MILLILITER,
            ingredients
        );
        ingredients.clear();

        assertThat(formula.getId()).isNotNull();
        assertThat(formula.getOutputInventoryItemId()).isEqualTo(outputItemId);
        assertThat(formula.getOutputQuantity()).isEqualByComparingTo("1000.000000");
        assertThat(formula.getIngredients()).containsExactly(ingredient);
        assertThatThrownBy(() -> formula.getIngredients().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectEmptyDuplicateOrNullIngredients() {
        var outputItemId = UUID.randomUUID();
        var ingredientItemId = UUID.randomUUID();

        assertThatThrownBy(() -> ProductionFormula.create(
            outputItemId,
            BigDecimal.ONE,
            UnitOfMeasure.UNIT,
            List.of()
        ))
            .isInstanceOf(InvalidProductionFormulaException.class)
            .hasMessage("Formula must contain at least one ingredient");

        assertThatThrownBy(() -> ProductionFormula.create(
            outputItemId,
            BigDecimal.ONE,
            UnitOfMeasure.UNIT,
            List.of(
                new FormulaIngredient(ingredientItemId, BigDecimal.ONE, UnitOfMeasure.UNIT),
                new FormulaIngredient(ingredientItemId, new BigDecimal("2"), UnitOfMeasure.UNIT)
            )
        ))
            .isInstanceOf(InvalidProductionFormulaException.class)
            .hasMessage("Formula must not contain the same ingredient inventory item more than once");

        assertThatThrownBy(() -> ProductionFormula.create(
            outputItemId,
            BigDecimal.ONE,
            UnitOfMeasure.UNIT,
            Arrays.asList((FormulaIngredient) null)
        ))
            .isInstanceOf(InvalidProductionFormulaException.class)
            .hasMessage("Formula ingredients must not contain null entries");
    }

    @Test
    void shouldRejectInvalidQuantities() {
        assertThatThrownBy(() -> new FormulaIngredient(
            UUID.randomUUID(), BigDecimal.ZERO, UnitOfMeasure.GRAM
        ))
            .isInstanceOf(InvalidProductionFormulaException.class)
            .hasMessage("Quantity must be greater than zero");

        assertThatThrownBy(() -> ProductionFormula.create(
            UUID.randomUUID(),
            new BigDecimal("1.0000001"),
            UnitOfMeasure.MILLILITER,
            List.of(new FormulaIngredient(UUID.randomUUID(), BigDecimal.ONE, UnitOfMeasure.MILLILITER))
        ))
            .isInstanceOf(InvalidProductionFormulaException.class)
            .hasMessage("Quantity must have at most 13 integer digits and 6 fractional digits");
    }

    @Test
    void shouldReplaceDefinitionWithoutChangingIdentity() {
        var formula = ProductionFormula.create(
            UUID.randomUUID(),
            new BigDecimal("100"),
            UnitOfMeasure.MILLILITER,
            List.of(new FormulaIngredient(UUID.randomUUID(), new BigDecimal("10"), UnitOfMeasure.MILLILITER))
        );
        var formulaId = formula.getId();
        var newOutputItemId = UUID.randomUUID();
        var newIngredient = new FormulaIngredient(UUID.randomUUID(), new BigDecimal("25"), UnitOfMeasure.GRAM);

        formula.replaceDefinition(
            newOutputItemId,
            new BigDecimal("500"),
            UnitOfMeasure.GRAM,
            List.of(newIngredient)
        );

        assertThat(formula.getId()).isEqualTo(formulaId);
        assertThat(formula.getOutputInventoryItemId()).isEqualTo(newOutputItemId);
        assertThat(formula.getOutputQuantity()).isEqualByComparingTo("500");
        assertThat(formula.getOutputUnitOfMeasure()).isEqualTo(UnitOfMeasure.GRAM);
        assertThat(formula.getIngredients()).containsExactly(newIngredient);
    }
}
