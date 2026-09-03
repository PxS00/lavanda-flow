package com.ceudelavanda.lavandaflow.production.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.production.domain.FormulaIngredient;
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormula;

final class ProductionFormulaMapper {

    private ProductionFormulaMapper() {
    }

    static ProductionFormulaJpaEntity toEntity(ProductionFormula formula) {
        return new ProductionFormulaJpaEntity(
            formula.getId(),
            formula.getOutputInventoryItemId(),
            formula.getOutputQuantity(),
            formula.getOutputUnitOfMeasure(),
            formula.getIngredients().stream()
                .map(ingredient -> new FormulaIngredientJpaValue(
                    ingredient.inventoryItemId(),
                    ingredient.quantity(),
                    ingredient.unitOfMeasure()
                ))
                .toList()
        );
    }

    static ProductionFormula toDomain(ProductionFormulaJpaEntity entity) {
        return new ProductionFormula(
            entity.getId(),
            entity.getOutputInventoryItemId(),
            entity.getOutputQuantity(),
            entity.getOutputUnitOfMeasure(),
            entity.getIngredients().stream()
                .map(ingredient -> new FormulaIngredient(
                    ingredient.getInventoryItemId(),
                    ingredient.getQuantity(),
                    ingredient.getUnitOfMeasure()
                ))
                .toList()
        );
    }
}
