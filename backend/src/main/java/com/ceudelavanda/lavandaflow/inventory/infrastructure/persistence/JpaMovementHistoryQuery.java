package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.inventory.application.MovementHistoryPage;
import com.ceudelavanda.lavandaflow.inventory.application.MovementHistoryQuery;
import com.ceudelavanda.lavandaflow.inventory.application.query.GetMovementHistoryQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaMovementHistoryQuery implements MovementHistoryQuery {

    private final SpringDataStockMovementRepository repository;

    @Override
    public MovementHistoryPage find(GetMovementHistoryQuery query) {
        var pageable = PageRequest.of(query.page(), query.size());
        var page = repository.findMovementHistory(
            query.inventoryItemId(),
            query.batchId(),
            query.type(),
            query.from() != null,
            query.from(),
            query.to() != null,
            query.to(),
            pageable
        );

        return new MovementHistoryPage(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
