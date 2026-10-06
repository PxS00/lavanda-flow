package com.ceudelavanda.lavandaflow.sales.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.sales.application.*;
import com.ceudelavanda.lavandaflow.sales.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import jakarta.persistence.criteria.Predicate;
import java.time.ZoneOffset;
import java.util.ArrayList;

@Repository
@RequiredArgsConstructor
class JpaOrderQuery implements OrderQuery {
    private final SpringDataOrderRepository repository;
    public OrderPage search(OrderSearchQuery query) {
        var page = repository.findAll((root, criteria, cb) -> {
            var filters = new ArrayList<Predicate>();
            filters.add(cb.equal(root.get("status"), OrderStatus.DRAFT));
            if (query.customerId() != null) filters.add(cb.equal(root.get("customerId"), query.customerId()));
            if (query.q() != null) filters.add(cb.like(root.get("id").cast(String.class), "%" + query.q()
                .replace("!", "!!").replace("%", "!%").replace("_", "!_").toLowerCase(java.util.Locale.ROOT) + "%", '!'));
            if (query.from() != null) filters.add(cb.greaterThanOrEqualTo(root.get("createdAt"), query.from().atStartOfDay().toInstant(ZoneOffset.UTC)));
            if (query.to() != null) filters.add(cb.lessThan(root.get("createdAt"), query.to().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            return cb.and(filters.toArray(Predicate[]::new));
        }, PageRequest.of(query.page(), query.size(), Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));
        return new OrderPage(page.getContent().stream().map(OrderJpaEntity::toDomain).toList(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
