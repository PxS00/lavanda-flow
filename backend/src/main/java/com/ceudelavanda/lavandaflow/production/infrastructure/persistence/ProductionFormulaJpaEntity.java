package com.ceudelavanda.lavandaflow.production.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Entity
@Table(name = "production_formula")
@NoArgsConstructor
class ProductionFormulaJpaEntity {

    @Id
    private UUID id;

    @Column(name = "output_inventory_item_id", nullable = false)
    private UUID outputInventoryItemId;

    @Column(name = "output_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal outputQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "output_unit_of_measure", nullable = false, length = 32)
    private UnitOfMeasure outputUnitOfMeasure;

    @ElementCollection
    @CollectionTable(
        name = "production_formula_ingredient",
        joinColumns = @JoinColumn(name = "formula_id")
    )
    @OrderColumn(name = "position")
    private List<FormulaIngredientJpaValue> ingredients = new ArrayList<>();

    ProductionFormulaJpaEntity(
        UUID id,
        UUID outputInventoryItemId,
        BigDecimal outputQuantity,
        UnitOfMeasure outputUnitOfMeasure,
        List<FormulaIngredientJpaValue> ingredients
    ) {
        this.id = id;
        this.outputInventoryItemId = outputInventoryItemId;
        this.outputQuantity = outputQuantity;
        this.outputUnitOfMeasure = outputUnitOfMeasure;
        this.ingredients = new ArrayList<>(ingredients);
    }
}
