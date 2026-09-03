package com.ceudelavanda.lavandaflow.production.application.formula;

import com.ceudelavanda.lavandaflow.production.domain.ProductionFormula;
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormulaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates a production formula after resolving current catalog-owned item facts. */
@Service
@RequiredArgsConstructor
public class CreateProductionFormula {

    private final ProductionFormulaRepository productionFormulaRepository;
    private final ProductionFormulaDefinitionResolver definitionResolver;

    @Transactional
    public ProductionFormulaResult execute(ProductionFormulaDefinitionCommand command) {
        var definition = definitionResolver.resolve(command);
        var formula = ProductionFormula.create(
            definition.outputItem().inventoryItemId(),
            command.outputQuantity(),
            definition.outputItem().unitOfMeasure(),
            definition.ingredients()
        );
        return ProductionFormulaResult.from(productionFormulaRepository.save(formula));
    }
}
