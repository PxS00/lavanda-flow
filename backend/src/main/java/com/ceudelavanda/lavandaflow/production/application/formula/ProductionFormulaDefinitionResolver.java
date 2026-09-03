package com.ceudelavanda.lavandaflow.production.application.formula;

import com.ceudelavanda.lavandaflow.catalog.ProductionItemReference;
import com.ceudelavanda.lavandaflow.catalog.ProductionItemReferenceLookup;
import com.ceudelavanda.lavandaflow.production.domain.FormulaIngredient;
import com.ceudelavanda.lavandaflow.production.domain.exception.InvalidProductionFormulaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class ProductionFormulaDefinitionResolver {

    private final ProductionItemReferenceLookup productionItemReferenceLookup;

    ResolvedDefinition resolve(ProductionFormulaDefinitionCommand command) {
        if (command == null) {
            throw new InvalidProductionFormulaException("formula", "Formula definition must not be null");
        }

        var output = requireActiveCatalogItem(command.outputInventoryItemId());
        var ingredients = resolveIngredients(command.ingredients());

        return new ResolvedDefinition(output, ingredients);
    }

    private List<FormulaIngredient> resolveIngredients(List<ProductionFormulaIngredientCommand> ingredients) {
        if (ingredients == null) {
            throw new InvalidProductionFormulaException("ingredients", "Formula must contain at least one ingredient");
        }

        return ingredients.stream()
            .map(ingredient -> {
                if (ingredient == null) {
                    throw new InvalidProductionFormulaException(
                        "ingredients",
                        "Formula ingredients must not contain null entries"
                    );
                }
                var item = requireActiveCatalogItem(ingredient.inventoryItemId());
                return new FormulaIngredient(item.inventoryItemId(), ingredient.quantity(), item.unitOfMeasure());
            })
            .toList();
    }

    private ProductionItemReference requireActiveCatalogItem(UUID inventoryItemId) {
        if (inventoryItemId == null) {
            throw new InvalidProductionFormulaException("inventoryItemId", "Inventory item must not be null");
        }

        var item = productionItemReferenceLookup.findByInventoryItemId(inventoryItemId)
            .orElseThrow(() -> new ProductionFormulaCatalogItemNotFoundException(inventoryItemId));

        if (!item.active()) {
            throw new InactiveProductionFormulaCatalogItemException(inventoryItemId);
        }
        return item;
    }

    record ResolvedDefinition(
        ProductionItemReference outputItem,
        List<FormulaIngredient> ingredients
    ) {
    }
}
