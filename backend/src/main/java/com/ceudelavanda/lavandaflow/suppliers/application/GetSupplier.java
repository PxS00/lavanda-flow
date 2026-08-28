package com.ceudelavanda.lavandaflow.suppliers.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Retrieves one supplier by identifier. */
@Service
@RequiredArgsConstructor
public class GetSupplier {

    private final SupplierQuery supplierQuery;

    @Transactional(readOnly = true)
    public SupplierResult execute(UUID supplierId) {
        return supplierQuery.findById(supplierId)
            .orElseThrow(() -> new SupplierNotFoundException(supplierId));
    }
}
