package com.ceudelavanda.lavandaflow.suppliers.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.suppliers.domain.Supplier;
import com.ceudelavanda.lavandaflow.suppliers.domain.SupplierRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaSupplierRepository implements SupplierRepository {

    private final SpringDataSupplierRepository repository;

    JpaSupplierRepository(SpringDataSupplierRepository repository) {
        this.repository = repository;
    }

    @Override
    public Supplier save(Supplier supplier) {
        var entity = SupplierMapper.toEntity(supplier);
        var savedEntity = repository.save(entity);

        return SupplierMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Supplier> findById(UUID id) {
        return repository.findById(id)
            .map(SupplierMapper::toDomain);
    }
}
