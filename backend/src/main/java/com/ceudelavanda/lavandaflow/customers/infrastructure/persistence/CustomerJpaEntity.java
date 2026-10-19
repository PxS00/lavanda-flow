package com.ceudelavanda.lavandaflow.customers.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.customers.domain.Customer;
import com.ceudelavanda.lavandaflow.customers.domain.CustomerContact;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer")
@NoArgsConstructor
class CustomerJpaEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 160) private String name;
    @Column(length = 16) private String phone;
    @Column(length = 254) private String email;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    CustomerJpaEntity(Customer customer) {
        id = customer.id();
        name = customer.contact().name();
        phone = customer.contact().phone();
        email = customer.contact().email();
        active = customer.active();
        createdAt = customer.createdAt();
        updatedAt = customer.updatedAt();
    }

    Customer toDomain() {
        return new Customer(id, new CustomerContact(name, phone, email), active, createdAt, updatedAt);
    }
}
