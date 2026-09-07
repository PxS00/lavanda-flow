package com.ceudelavanda.lavandaflow.production.application.formula;

import com.ceudelavanda.lavandaflow.production.domain.ProductionFormulaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Lists the current production formula definitions in deterministic identifier order. */
@Service
@RequiredArgsConstructor
public class ListProductionFormulas {

    private final ProductionFormulaRepository productionFormulaRepository;

    @Transactional(readOnly = true)
    public List<ProductionFormulaResult> execute() {
        return productionFormulaRepository.findAll().stream()
            .map(ProductionFormulaResult::from)
            .toList();
    }
}
