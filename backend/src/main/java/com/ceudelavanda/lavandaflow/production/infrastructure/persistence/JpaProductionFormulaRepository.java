package com.ceudelavanda.lavandaflow.production.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.production.domain.ProductionFormula;
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormulaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaProductionFormulaRepository implements ProductionFormulaRepository {

    private final SpringDataProductionFormulaRepository repository;

    JpaProductionFormulaRepository(SpringDataProductionFormulaRepository repository) {
        this.repository = repository;
    }

    @Override
    public ProductionFormula save(ProductionFormula formula) {
        return ProductionFormulaMapper.toDomain(repository.save(ProductionFormulaMapper.toEntity(formula)));
    }

    @Override
    public Optional<ProductionFormula> findById(UUID formulaId) {
        return repository.findById(formulaId)
            .map(ProductionFormulaMapper::toDomain);
    }

    @Override
    public List<ProductionFormula> findAll() {
        return repository.findAllByOrderByIdAsc().stream()
            .map(ProductionFormulaMapper::toDomain)
            .toList();
    }
}
