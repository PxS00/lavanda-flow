package com.ceudelavanda.lavandaflow.production.application.formula;

import com.ceudelavanda.lavandaflow.production.domain.ProductionFormulaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Retrieves one production formula by its stable identifier. */
@Service
@RequiredArgsConstructor
public class GetProductionFormula {

    private final ProductionFormulaRepository productionFormulaRepository;

    @Transactional(readOnly = true)
    public ProductionFormulaResult execute(UUID formulaId) {
        return productionFormulaRepository.findById(formulaId)
            .map(ProductionFormulaResult::from)
            .orElseThrow(() -> new ProductionFormulaNotFoundException(formulaId));
    }
}
