package com.ceudelavanda.lavandaflow.suppliers.application;

import com.ceudelavanda.lavandaflow.suppliers.SupplierLookup;
import com.ceudelavanda.lavandaflow.suppliers.SupplierSnapshot;
import com.ceudelavanda.lavandaflow.suppliers.domain.Supplier;
import com.ceudelavanda.lavandaflow.suppliers.domain.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class SuppliersSupplierLookup implements SupplierLookup {

    private final SupplierRepository supplierRepository;

    @Override
    public Optional<SupplierSnapshot> findById(UUID supplierId) {
        return supplierRepository.findById(supplierId).map(SuppliersSupplierLookup::toSnapshot);
    }

    private static SupplierSnapshot toSnapshot(Supplier supplier) {
        return new SupplierSnapshot(supplier.getId(), supplier.getName(), supplier.isActive());
    }
}
