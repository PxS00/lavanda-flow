package com.ceudelavanda.lavandaflow.customers.application;

import com.ceudelavanda.lavandaflow.customers.domain.Customer;
import com.ceudelavanda.lavandaflow.customers.domain.CustomerContact;
import com.ceudelavanda.lavandaflow.customers.domain.CustomerRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Customer maintenance transaction boundary. All writes validate normalized contacts using
 * standard Bean Validation, including @Email. Edits and activation actions lock the same row;
 * deactivation retains the contact and audit creation time for future historical references.
 */
@Service
@RequiredArgsConstructor
public class CustomerManagement {
    private final CustomerRepository repository;
    private final CustomerQuery query;
    private final Validator validator;
    private final Clock clock;

    /** Registers an active contact; duplicate contact methods are allowed.
     * @throws InvalidCustomerContactException when normalized values violate the contact contract
     */
    @Transactional
    public CustomerResult register(CustomerContact contact) {
        validate(contact);
        return CustomerResult.from(repository.save(Customer.create(contact, Instant.now(clock))));
    }

    /** Retrieves either active or inactive contact values.
     * @throws CustomerNotFoundException when the stable identity does not exist
     */
    @Transactional(readOnly = true)
    public CustomerResult getById(UUID id) {
        return CustomerResult.from(repository.findById(id).orElseThrow(() -> new CustomerNotFoundException(id)));
    }

    /** Lists a validated, bounded query without changing contacts. */
    @Transactional(readOnly = true)
    public CustomerPage search(CustomerSearchQuery search) {
        return query.search(search);
    }

    /** Replaces the supported contact fields, preserving identity, creation time and active state. */
    @Transactional
    public CustomerResult update(UUID id, CustomerContact contact) {
        validate(contact);
        var customer = repository.findByIdForUpdate(id).orElseThrow(() -> new CustomerNotFoundException(id));
        return CustomerResult.from(repository.save(customer.update(contact, Instant.now(clock))));
    }

    /** Idempotent state transition: a repeated action leaves the audit timestamp unchanged. */
    @Transactional
    public CustomerResult changeActiveState(UUID id, boolean active) {
        var customer = repository.findByIdForUpdate(id).orElseThrow(() -> new CustomerNotFoundException(id));
        return CustomerResult.from(customer.active() == active ? customer
            : repository.save(customer.changeActiveState(active, Instant.now(clock))));
    }

    private void validate(CustomerContact contact) {
        var violations = validator.validate(contact);
        if (!violations.isEmpty()) {
            var details = violations.stream().collect(Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                violation -> violation.getMessage(), (first, second) -> first));
            throw new InvalidCustomerContactException(details);
        }
    }
}
