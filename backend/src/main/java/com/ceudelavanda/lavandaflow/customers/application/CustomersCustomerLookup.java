package com.ceudelavanda.lavandaflow.customers.application;

import com.ceudelavanda.lavandaflow.customers.CustomerLookup;
import com.ceudelavanda.lavandaflow.customers.CustomerSnapshot;
import com.ceudelavanda.lavandaflow.customers.domain.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class CustomersCustomerLookup implements CustomerLookup {
    private final CustomerRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerSnapshot> findById(UUID customerId) {
        return repository.findById(customerId).map(customer -> new CustomerSnapshot(customer.id(),
            customer.contact().name(), customer.contact().phone(), customer.contact().email(), customer.active()));
    }
}
