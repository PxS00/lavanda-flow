package com.ceudelavanda.lavandaflow.suppliers.application;

import com.ceudelavanda.lavandaflow.suppliers.domain.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maintains the supported fields of an existing supplier without replacing its identity. */
@Service
@RequiredArgsConstructor
public class UpdateSupplier {

    private final SupplierRepository supplierRepository;

    @Transactional
    public SupplierResult execute(UpdateSupplierCommand command) {
        var supplier = supplierRepository.findById(command.supplierId())
            .orElseThrow(() -> new SupplierNotFoundException(command.supplierId()));

        supplier.rename(command.name());
        supplier.changeIdentifier(command.identifier());
        supplier.changeContact(command.contact());
        supplier.changeNotes(command.notes());
        if (command.active()) {
            supplier.activate();
        } else {
            supplier.deactivate();
        }

        return SupplierResult.from(supplierRepository.save(supplier));
    }
}
