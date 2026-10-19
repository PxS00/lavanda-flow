package com.ceudelavanda.lavandaflow.sales.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.sales.domain.Order;
import com.ceudelavanda.lavandaflow.sales.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JpaOrderRepository implements OrderRepository {
    private final SpringDataOrderRepository repository;
    public Order save(Order order) {
        var entity = repository.findById(order.id()).orElseGet(() -> new OrderJpaEntity(order));
        entity.replace(order);
        return repository.saveAndFlush(entity).toDomain();
    }
    public Optional<Order> findById(UUID id) { return repository.findById(id).map(OrderJpaEntity::toDomain); }
    public Optional<Order> findByIdForUpdate(UUID id) { return repository.findByIdForUpdate(id).map(OrderJpaEntity::toDomain); }
}
