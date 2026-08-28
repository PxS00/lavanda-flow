package com.ceudelavanda.lavandaflow.suppliers.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Searches suppliers through framework-neutral filters and pagination. */
@Service
@RequiredArgsConstructor
public class SearchSuppliers {

    private final SupplierQuery supplierQuery;

    @Transactional(readOnly = true)
    public SupplierPage execute(SupplierSearchQuery query) {
        return supplierQuery.search(query);
    }
}
