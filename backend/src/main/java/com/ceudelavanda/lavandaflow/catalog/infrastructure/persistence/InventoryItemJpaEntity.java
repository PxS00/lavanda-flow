package com.ceudelavanda.lavandaflow.catalog.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Getter
@Entity
@Table(name = "inventory_item")
@NoArgsConstructor
class InventoryItemJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_unit", nullable = false)
    private UnitOfMeasure unitOfMeasure;

    @Column(nullable = false)
    private boolean active;

    InventoryItemJpaEntity(
        UUID id,
        String name,
        String description,
        Category category,
        UnitOfMeasure unitOfMeasure,
        boolean active
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.unitOfMeasure = unitOfMeasure;
        this.active = active;
    }
}
