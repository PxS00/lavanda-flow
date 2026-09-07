package com.ceudelavanda.lavandaflow.production.application.formula;

import com.ceudelavanda.lavandaflow.catalog.ProductionItemReference;
import com.ceudelavanda.lavandaflow.catalog.ProductionItemReferenceLookup;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormula;
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormulaRepository;
import com.ceudelavanda.lavandaflow.production.domain.exception.InvalidProductionFormulaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionFormulaApplicationTest {

    @Mock
    private ProductionFormulaRepository productionFormulaRepository;

    @Mock
    private ProductionItemReferenceLookup productionItemReferenceLookup;

    @Test
    void shouldCreateFormulaUsingCatalogOwnedUnits() {
        var outputItemId = UUID.randomUUID();
        var ingredientItemId = UUID.randomUUID();
        when(productionItemReferenceLookup.findByInventoryItemId(outputItemId))
            .thenReturn(Optional.of(reference(outputItemId, UnitOfMeasure.LITER, true)));
        when(productionItemReferenceLookup.findByInventoryItemId(ingredientItemId))
            .thenReturn(Optional.of(reference(ingredientItemId, UnitOfMeasure.MILLILITER, true)));
        when(productionFormulaRepository.save(any(ProductionFormula.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        var resolver = new ProductionFormulaDefinitionResolver(productionItemReferenceLookup);
        var useCase = new CreateProductionFormula(productionFormulaRepository, resolver);

        var result = useCase.execute(definition(
            outputItemId,
            "2",
            new ProductionFormulaIngredientCommand(ingredientItemId, new BigDecimal("250"))
        ));

        assertThat(result.outputUnitOfMeasure()).isEqualTo(UnitOfMeasure.LITER);
        assertThat(result.ingredients()).singleElement()
            .satisfies(ingredient -> assertThat(ingredient.unitOfMeasure()).isEqualTo(UnitOfMeasure.MILLILITER));
        verify(productionFormulaRepository).save(any(ProductionFormula.class));
    }

    @Test
    void shouldRejectMissingOrInactiveCatalogItems() {
        var missingItemId = UUID.randomUUID();
        var resolver = new ProductionFormulaDefinitionResolver(productionItemReferenceLookup);
        var useCase = new CreateProductionFormula(productionFormulaRepository, resolver);

        when(productionItemReferenceLookup.findByInventoryItemId(missingItemId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(definition(
            missingItemId,
            "1",
            new ProductionFormulaIngredientCommand(UUID.randomUUID(), BigDecimal.ONE)
        )))
            .isInstanceOf(ProductionFormulaCatalogItemNotFoundException.class);

        var inactiveItemId = UUID.randomUUID();
        when(productionItemReferenceLookup.findByInventoryItemId(inactiveItemId))
            .thenReturn(Optional.of(reference(inactiveItemId, UnitOfMeasure.UNIT, false)));
        assertThatThrownBy(() -> useCase.execute(definition(
            inactiveItemId,
            "1",
            new ProductionFormulaIngredientCommand(UUID.randomUUID(), BigDecimal.ONE)
        )))
            .isInstanceOf(InactiveProductionFormulaCatalogItemException.class);

        verify(productionFormulaRepository, never()).save(any());
    }

    @Test
    void shouldRejectDuplicateIngredientMisuseBeforePersistence() {
        var outputItemId = UUID.randomUUID();
        var ingredientItemId = UUID.randomUUID();
        when(productionItemReferenceLookup.findByInventoryItemId(outputItemId))
            .thenReturn(Optional.of(reference(outputItemId, UnitOfMeasure.MILLILITER, true)));
        when(productionItemReferenceLookup.findByInventoryItemId(ingredientItemId))
            .thenReturn(Optional.of(reference(ingredientItemId, UnitOfMeasure.MILLILITER, true)));
        var resolver = new ProductionFormulaDefinitionResolver(productionItemReferenceLookup);
        var useCase = new CreateProductionFormula(productionFormulaRepository, resolver);

        var command = new ProductionFormulaDefinitionCommand(
            outputItemId,
            new BigDecimal("100"),
            List.of(
                new ProductionFormulaIngredientCommand(ingredientItemId, BigDecimal.ONE),
                new ProductionFormulaIngredientCommand(ingredientItemId, new BigDecimal("2"))
            )
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(InvalidProductionFormulaException.class);
        verify(productionFormulaRepository, never()).save(any());
    }

    @Test
    void shouldReplaceCurrentFormulaDefinitionWhilePreservingIdentity() {
        var originalOutputId = UUID.randomUUID();
        var originalIngredientId = UUID.randomUUID();
        var formula = ProductionFormula.create(
            originalOutputId,
            new BigDecimal("100"),
            UnitOfMeasure.MILLILITER,
            List.of(new com.ceudelavanda.lavandaflow.production.domain.FormulaIngredient(
                originalIngredientId, new BigDecimal("10"), UnitOfMeasure.MILLILITER
            ))
        );
        var newOutputId = UUID.randomUUID();
        var newIngredientId = UUID.randomUUID();
        when(productionFormulaRepository.findById(formula.getId())).thenReturn(Optional.of(formula));
        when(productionItemReferenceLookup.findByInventoryItemId(newOutputId))
            .thenReturn(Optional.of(reference(newOutputId, UnitOfMeasure.GRAM, true)));
        when(productionItemReferenceLookup.findByInventoryItemId(newIngredientId))
            .thenReturn(Optional.of(reference(newIngredientId, UnitOfMeasure.KILOGRAM, true)));
        when(productionFormulaRepository.save(any(ProductionFormula.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        var resolver = new ProductionFormulaDefinitionResolver(productionItemReferenceLookup);
        var useCase = new UpdateProductionFormula(productionFormulaRepository, resolver);

        var result = useCase.execute(formula.getId(), definition(
            newOutputId,
            "5",
            new ProductionFormulaIngredientCommand(newIngredientId, new BigDecimal("1"))
        ));

        assertThat(result.id()).isEqualTo(formula.getId());
        assertThat(result.outputInventoryItemId()).isEqualTo(newOutputId);
        assertThat(result.outputUnitOfMeasure()).isEqualTo(UnitOfMeasure.GRAM);
        assertThat(result.ingredients()).singleElement()
            .satisfies(ingredient -> {
                assertThat(ingredient.inventoryItemId()).isEqualTo(newIngredientId);
                assertThat(ingredient.unitOfMeasure()).isEqualTo(UnitOfMeasure.KILOGRAM);
            });
    }

    @Test
    void shouldGetAndListPersistedFormulaDefinitions() {
        var formula = ProductionFormula.create(
            UUID.randomUUID(),
            BigDecimal.ONE,
            UnitOfMeasure.UNIT,
            List.of(new com.ceudelavanda.lavandaflow.production.domain.FormulaIngredient(
                UUID.randomUUID(), BigDecimal.ONE, UnitOfMeasure.UNIT
            ))
        );
        when(productionFormulaRepository.findById(formula.getId())).thenReturn(Optional.of(formula));
        when(productionFormulaRepository.findAll()).thenReturn(List.of(formula));

        var get = new GetProductionFormula(productionFormulaRepository);
        var list = new ListProductionFormulas(productionFormulaRepository);

        assertThat(get.execute(formula.getId()).id()).isEqualTo(formula.getId());
        assertThat(list.execute()).extracting(ProductionFormulaResult::id).containsExactly(formula.getId());
    }

    @Test
    void shouldRejectUpdateForMissingFormula() {
        var formulaId = UUID.randomUUID();
        when(productionFormulaRepository.findById(formulaId)).thenReturn(Optional.empty());
        var resolver = new ProductionFormulaDefinitionResolver(productionItemReferenceLookup);
        var useCase = new UpdateProductionFormula(productionFormulaRepository, resolver);

        assertThatThrownBy(() -> useCase.execute(formulaId, definition(
            UUID.randomUUID(),
            "1",
            new ProductionFormulaIngredientCommand(UUID.randomUUID(), BigDecimal.ONE)
        )))
            .isInstanceOf(ProductionFormulaNotFoundException.class);

        verify(productionItemReferenceLookup, never()).findByInventoryItemId(any());
    }

    private static ProductionFormulaDefinitionCommand definition(
        UUID outputItemId,
        String outputQuantity,
        ProductionFormulaIngredientCommand... ingredients
    ) {
        return new ProductionFormulaDefinitionCommand(
            outputItemId,
            new BigDecimal(outputQuantity),
            List.of(ingredients)
        );
    }

    private static ProductionItemReference reference(UUID id, UnitOfMeasure unit, boolean active) {
        return new ProductionItemReference(id, unit, active, null, null);
    }
}
