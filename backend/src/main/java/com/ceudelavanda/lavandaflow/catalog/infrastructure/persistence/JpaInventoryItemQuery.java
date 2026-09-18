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
import java.util.Set;
import java.util.UUID;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;

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
        var page = repository.search(toNamePattern(query.name()), query.category(), query.active(), pageable);
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

    @Override
    public InventoryItemPage findStockPage(Set<Category> categories, int page, int size) {
        var pageable = PageRequest.of(page, size);
        var result = categories.isEmpty()
            ? repository.findStockPage(pageable)
            : repository.findStockPageByCategoryIn(categories, pageable);
        return new InventoryItemPage(
            result.getContent().stream()
                .map(InventoryItemMapper::toDomain)
                .map(InventoryItemResult::from)
                .toList(),
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }

    private static String toNamePattern(String name) {
        if (name == null) {
            return null;
        }
        var escaped = name.toLowerCase(Locale.ROOT)
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
