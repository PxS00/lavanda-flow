package com.ceudelavanda.lavandaflow.customers.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.customers.domain.Customer;
import com.ceudelavanda.lavandaflow.customers.domain.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JpaCustomerRepository implements CustomerRepository {
    private final SpringDataCustomerRepository repository;

    @Override
    public Customer save(Customer customer) {
        return repository.save(new CustomerJpaEntity(customer)).toDomain();
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return repository.findById(id).map(CustomerJpaEntity::toDomain);
    }

    @Override
    public List<Customer> findByIds(Collection<UUID> ids) {
        return repository.findAllById(ids).stream().map(CustomerJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<Customer> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(CustomerJpaEntity::toDomain);
    }
}
