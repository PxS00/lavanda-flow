package com.ceudelavanda.lavandaflow.suppliers.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.suppliers.domain.Supplier;

final class SupplierMapper {

    private SupplierMapper() {
    }

    static SupplierJpaEntity toEntity(Supplier supplier) {
        return new SupplierJpaEntity(
            supplier.getId(),
            supplier.getName(),
            supplier.getIdentifier(),
            supplier.getContact(),
            supplier.getNotes(),
            supplier.isActive()
        );
    }

    static Supplier toDomain(SupplierJpaEntity entity) {
        return new Supplier(
            entity.getId(),
            entity.getName(),
            entity.getIdentifier(),
            entity.getContact(),
            entity.getNotes(),
            entity.isActive()
        );
    }
}
