package com.ceudelavanda.lavandaflow.production.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.production.domain.ProductionLotCodeMode;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Entity
@Table(name = "production_execution")
@NoArgsConstructor
class ProductionExecutionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "formula_id", nullable = false)
    private UUID formulaId;

    @Column(name = "output_inventory_item_id", nullable = false)
    private UUID outputInventoryItemId;

    @Column(name = "output_batch_id", nullable = false, unique = true)
    private UUID outputBatchId;

    @Column(name = "output_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal outputQuantity;

    @Column(name = "lot_code", nullable = false, length = 255)
    private String lotCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "lot_code_mode", nullable = false, length = 16)
    private ProductionLotCodeMode lotCodeMode;

    @Column(name = "production_date", nullable = false)
    private LocalDate productionDate;

    @Column(name = "output_received_at", nullable = false)
    private LocalDate outputReceivedAt;

    @Column(name = "output_expires_at")
    private LocalDate outputExpiresAt;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @ElementCollection
    @CollectionTable(
        name = "production_consumption",
        joinColumns = @JoinColumn(name = "execution_id")
    )
    @OrderColumn(name = "position")
    private List<ProductionConsumptionJpaValue> consumptions = new ArrayList<>();

    ProductionExecutionJpaEntity(
        UUID id,
        UUID formulaId,
        UUID outputInventoryItemId,
        UUID outputBatchId,
        BigDecimal outputQuantity,
        String lotCode,
        ProductionLotCodeMode lotCodeMode,
        LocalDate productionDate,
        LocalDate outputReceivedAt,
        LocalDate outputExpiresAt,
        Instant completedAt,
        List<ProductionConsumptionJpaValue> consumptions
    ) {
        this.id = id;
        this.formulaId = formulaId;
        this.outputInventoryItemId = outputInventoryItemId;
        this.outputBatchId = outputBatchId;
        this.outputQuantity = outputQuantity;
        this.lotCode = lotCode;
        this.lotCodeMode = lotCodeMode;
        this.productionDate = productionDate;
        this.outputReceivedAt = outputReceivedAt;
        this.outputExpiresAt = outputExpiresAt;
        this.completedAt = completedAt;
        this.consumptions = new ArrayList<>(consumptions);
    }
}
