package com.ceudelavanda.lavandaflow.catalog.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.catalog.application.InventoryItemPage;
import com.ceudelavanda.lavandaflow.catalog.application.InventoryItemQuery;
import com.ceudelavanda.lavandaflow.catalog.application.InventoryItemResult;
import com.ceudelavanda.lavandaflow.catalog.application.InventoryItemSearchQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JpaInventoryItemQuery implements InventoryItemQuery {

    private final SpringDataInventoryItemRepository repository;

    @Override
    public Optional<InventoryItemResult> findById(UUID inventoryItemId) {
        return repository.findById(inventoryItemId)
            .map(InventoryItemMapper::toDomain)
            .map(InventoryItemResult::from);
    }

    @Override
    public InventoryItemPage search(InventoryItemSearchQuery query) {
        var pageable = PageRequest.of(query.page(), query.size());
        var namePattern = query.name() == null
            ? null
            : "%" + query.name().toLowerCase(Locale.ROOT) + "%";
        var page = repository.search(namePattern, query.category(), query.active(), pageable);
        return new InventoryItemPage(
            page.getContent().stream()
                .map(InventoryItemMapper::toDomain)
                .map(InventoryItemResult::from)
                .toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
