package com.ceudelavanda.lavandaflow.suppliers.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "supplier")
@NoArgsConstructor
class SupplierJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column
    private String identifier;

    @Column
    private String contact;

    @Column
    private String notes;

    @Column(nullable = false)
    private boolean active;

    SupplierJpaEntity(
        UUID id,
        String name,
        String identifier,
        String contact,
        String notes,
        boolean active
    ) {
        this.id = id;
        this.name = name;
        this.identifier = identifier;
        this.contact = contact;
        this.notes = notes;
        this.active = active;
    }
}
