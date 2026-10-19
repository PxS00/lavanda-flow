package com.ceudelavanda.lavandaflow.sales.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.sales.domain.*;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "sales_order")
@NoArgsConstructor
class OrderJpaEntity {
    @Id UUID id;
    UUID customerId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 9) OrderStatus status;
    @Column(nullable = false, precision = 19, scale = 2) BigDecimal total;
    @Column(nullable = false, updatable = false) Instant createdAt;
    @Column(nullable = false) Instant updatedAt;
    String customerName;
    String customerPhone;
    String customerEmail;
    Instant confirmedAt;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position") List<OrderLineJpaEntity> lines = new ArrayList<>();

    OrderJpaEntity(Order order) { id = order.id(); createdAt = order.createdAt(); }

    void replace(Order order) {
        customerName = order.customerName(); customerPhone = order.customerPhone(); customerEmail = order.customerEmail(); confirmedAt = order.confirmedAt();
        customerId = order.customerId(); status = order.status(); total = order.total(); updatedAt = order.updatedAt();
        var retained = order.lines().stream().map(OrderLine::id).toList();
        lines.removeIf(line -> !retained.contains(line.id));
        for (int i = 0; i < order.lines().size(); i++) {
            var value = order.lines().get(i);
            var entity = lines.stream().filter(line -> line.id.equals(value.id())).findFirst().orElse(null);
            if (entity == null) { entity = new OrderLineJpaEntity(this, value.id()); lines.add(entity); }
            entity.replace(value, i);
        }
    }

    Order toDomain() {
        return toDomain(lines.stream().sorted(Comparator.comparingInt(l -> l.position))
            .map(OrderLineJpaEntity::toDomain).toList());
    }

    Order toDomain(List<OrderLine> loadedLines) {
        return new Order(id, customerId, status, loadedLines, total, createdAt, updatedAt, customerName, customerPhone, customerEmail, confirmedAt);
    }
}
