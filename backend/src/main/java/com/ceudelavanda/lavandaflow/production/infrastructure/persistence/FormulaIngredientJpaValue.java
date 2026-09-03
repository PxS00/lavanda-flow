package com.ceudelavanda.lavandaflow.production.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Embeddable
@NoArgsConstructor
class FormulaIngredientJpaValue {

    @Column(name = "inventory_item_id", nullable = false)
    private UUID inventoryItemId;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_of_measure", nullable = false, length = 32)
    private UnitOfMeasure unitOfMeasure;

    FormulaIngredientJpaValue(
        UUID inventoryItemId,
        BigDecimal quantity,
        UnitOfMeasure unitOfMeasure
    ) {
        this.inventoryItemId = inventoryItemId;
        this.quantity = quantity;
        this.unitOfMeasure = unitOfMeasure;
    }
}
