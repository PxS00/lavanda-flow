package com.ceudelavanda.lavandaflow.sales.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.sales.domain.SaleAllocation;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import java.math.BigDecimal;
import java.util.UUID;

/** Sales-owned immutable UUID references; no inventory JPA associations. */
@Entity @Immutable @Table(name = "sale_allocation") @NoArgsConstructor
class SaleAllocationJpaEntity {
    @Id UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "line_id", nullable = false) OrderLineJpaEntity line;
    @Column(nullable = false) UUID batchId;
    @Column(nullable = false) UUID movementId;
    @Column(nullable = false, precision = 19, scale = 6) BigDecimal quantity;
    @Column(nullable = false) int position;
    SaleAllocationJpaEntity(OrderLineJpaEntity line, SaleAllocation value, int position) {
        id = UUID.randomUUID(); this.line = line; batchId = value.batchId(); movementId = value.movementId(); quantity = value.quantity(); this.position = position;
    }
    SaleAllocation toDomain() { return new SaleAllocation(batchId, movementId, quantity); }
}
