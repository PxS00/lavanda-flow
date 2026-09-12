package com.ceudelavanda.lavandaflow.production.application.formula;

import com.ceudelavanda.lavandaflow.catalog.ProductionItemReference;
import com.ceudelavanda.lavandaflow.catalog.ProductionItemReferenceLookup;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.production.domain.FormulaIngredient;
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormulaKind;
import com.ceudelavanda.lavandaflow.production.domain.exception.InvalidProductionFormulaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class ProductionFormulaDefinitionResolver {

    private static final String FINISHED_PRODUCT = "FINISHED_PRODUCT";

    private final ProductionItemReferenceLookup productionItemReferenceLookup;

    ResolvedDefinition resolve(ProductionFormulaDefinitionCommand command) {
        if (command == null) {
            throw new InvalidProductionFormulaException("formula", "Formula definition must not be null");
        }

        var output = requireActiveCatalogItem(command.outputInventoryItemId());
        var ingredientResolution = resolveIngredients(command.ingredients());
        var kind = command.effectiveKind();
        validateKind(kind, output, ingredientResolution.items());

        return new ResolvedDefinition(kind, output, ingredientResolution.ingredients());
    }

    private IngredientResolution resolveIngredients(List<ProductionFormulaIngredientCommand> ingredients) {
        if (ingredients == null) {
            throw new InvalidProductionFormulaException("ingredients", "Formula must contain at least one ingredient");
        }

        var resolvedIngredients = new ArrayList<FormulaIngredient>();
        var resolvedItems = new ArrayList<ProductionItemReference>();
        for (var ingredient : ingredients) {
            if (ingredient == null) {
                throw new InvalidProductionFormulaException(
                    "ingredients",
                    "Formula ingredients must not contain null entries"
                );
            }
            var item = requireActiveCatalogItem(ingredient.inventoryItemId());
            resolvedItems.add(item);
            resolvedIngredients.add(new FormulaIngredient(
                item.inventoryItemId(),
                ingredient.quantity(),
                item.unitOfMeasure()
            ));
        }
        return new IngredientResolution(List.copyOf(resolvedIngredients), List.copyOf(resolvedItems));
    }

    private void validateKind(
        ProductionFormulaKind kind,
        ProductionItemReference output,
        List<ProductionItemReference> ingredients
    ) {
        if (kind != ProductionFormulaKind.PACKAGED_FILLING) {
            return;
        }
        if (!FINISHED_PRODUCT.equals(output.category()) || output.unitOfMeasure() != UnitOfMeasure.UNIT) {
            throw new InvalidProductionFormulaException(
                "kind",
                "PACKAGED_FILLING requires a FINISHED_PRODUCT output measured in UNIT"
            );
        }
        var hasBulkFinishedProduct = ingredients.stream().anyMatch(item ->
            FINISHED_PRODUCT.equals(item.category()) && item.unitOfMeasure() != UnitOfMeasure.UNIT
        );
        if (!hasBulkFinishedProduct) {
            throw new InvalidProductionFormulaException(
                "ingredients",
                "PACKAGED_FILLING requires at least one non-UNIT FINISHED_PRODUCT ingredient"
            );
        }
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
        ProductionFormulaKind kind,
        ProductionItemReference outputItem,
        List<FormulaIngredient> ingredients
    ) {
    }

    private record IngredientResolution(
        List<FormulaIngredient> ingredients,
        List<ProductionItemReference> items
    ) {
    }
}
