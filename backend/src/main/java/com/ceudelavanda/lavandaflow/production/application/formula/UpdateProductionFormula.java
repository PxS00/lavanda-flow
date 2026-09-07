package com.ceudelavanda.lavandaflow.production.application.formula;

import com.ceudelavanda.lavandaflow.production.domain.ProductionFormulaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Replaces the current editable definition of an existing production formula. */
@Service
@RequiredArgsConstructor
public class UpdateProductionFormula {

    private final ProductionFormulaRepository productionFormulaRepository;
    private final ProductionFormulaDefinitionResolver definitionResolver;

    @Transactional
    public ProductionFormulaResult execute(UUID formulaId, ProductionFormulaDefinitionCommand command) {
        var formula = productionFormulaRepository.findById(formulaId)
            .orElseThrow(() -> new ProductionFormulaNotFoundException(formulaId));
        var definition = definitionResolver.resolve(command);

        formula.replaceDefinition(
            definition.outputItem().inventoryItemId(),
            command.outputQuantity(),
            definition.outputItem().unitOfMeasure(),
            definition.ingredients()
        );

        return ProductionFormulaResult.from(productionFormulaRepository.save(formula));
    }
}
