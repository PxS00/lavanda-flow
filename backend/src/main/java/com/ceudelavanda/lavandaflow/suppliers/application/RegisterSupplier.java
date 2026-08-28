package com.ceudelavanda.lavandaflow.suppliers.application;

import com.ceudelavanda.lavandaflow.suppliers.domain.Supplier;
import com.ceudelavanda.lavandaflow.suppliers.domain.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registers a new supplier using the existing supplier domain invariants. */
@Service
@RequiredArgsConstructor
public class RegisterSupplier {

    private final SupplierRepository supplierRepository;

    @Transactional
    public SupplierResult execute(RegisterSupplierCommand command) {
        var supplier = Supplier.create(
            command.name(),
            command.identifier(),
            command.contact(),
            command.notes()
        );
        return SupplierResult.from(supplierRepository.save(supplier));
    }
}
