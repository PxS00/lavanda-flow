package com.ceudelavanda.lavandaflow.production.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for production formula definitions. */
public interface ProductionFormulaRepository {

    ProductionFormula save(ProductionFormula formula);

    Optional<ProductionFormula> findById(UUID formulaId);

    List<ProductionFormula> findAll();
}
