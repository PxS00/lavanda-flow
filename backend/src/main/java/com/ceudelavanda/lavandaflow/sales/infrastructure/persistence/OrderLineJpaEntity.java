package com.ceudelavanda.lavandaflow.sales.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.sales.domain.OrderLine;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "sales_order_line")
@NoArgsConstructor
class OrderLineJpaEntity {
    @Id UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_id", nullable = false) OrderJpaEntity order;
    @Column(nullable = false) UUID itemId;
    @Column(nullable = false) int position;
    @Column(nullable = false, precision = 19, scale = 6) BigDecimal quantity;
    @Column(nullable = false, precision = 19, scale = 4) BigDecimal unitPrice;
    @Column(nullable = false, precision = 19, scale = 2) BigDecimal amount;

    OrderLineJpaEntity(OrderJpaEntity order, UUID id) { this.order = order; this.id = id; }
    void replace(OrderLine line, int position) {
        itemId = line.itemId(); quantity = line.quantity(); unitPrice = line.unitPrice(); amount = line.amount(); this.position = position;
    }
    UUID orderId() { return order.id; }
    OrderLine toDomain() { return new OrderLine(id, itemId, quantity, unitPrice, amount); }
}
