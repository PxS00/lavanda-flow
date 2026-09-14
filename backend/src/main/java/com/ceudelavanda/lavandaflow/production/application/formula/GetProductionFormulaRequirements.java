package com.ceudelavanda.lavandaflow.production.application.formula;

import com.ceudelavanda.lavandaflow.production.application.execution.ProductionRequirementCalculator;
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormulaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/** Returns backend-calculated requirements without reserving stock, batches, or lot sequences. */
@Service
@RequiredArgsConstructor
public class GetProductionFormulaRequirements {

    private final ProductionFormulaRepository productionFormulaRepository;
    private final ProductionRequirementCalculator productionRequirementCalculator;

    @Transactional(readOnly = true)
    public ProductionFormulaRequirementsResult execute(UUID formulaId, BigDecimal outputQuantity) {
        var formula = productionFormulaRepository.findById(formulaId)
            .orElseThrow(() -> new ProductionFormulaNotFoundException(formulaId));
        var requirements = productionRequirementCalculator.calculate(formula, outputQuantity);
        return ProductionFormulaRequirementsResult.from(formula, outputQuantity, requirements);
    }
}
