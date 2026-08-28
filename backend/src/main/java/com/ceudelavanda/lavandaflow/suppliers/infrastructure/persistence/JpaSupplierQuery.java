package com.ceudelavanda.lavandaflow.suppliers.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.suppliers.application.SupplierPage;
import com.ceudelavanda.lavandaflow.suppliers.application.SupplierQuery;
import com.ceudelavanda.lavandaflow.suppliers.application.SupplierResult;
import com.ceudelavanda.lavandaflow.suppliers.application.SupplierSearchQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JpaSupplierQuery implements SupplierQuery {

    private final SpringDataSupplierRepository repository;

    @Override
    public Optional<SupplierResult> findById(UUID supplierId) {
        return repository.findById(supplierId)
            .map(SupplierMapper::toDomain)
            .map(SupplierResult::from);
    }

    @Override
    public SupplierPage search(SupplierSearchQuery query) {
        var pageable = PageRequest.of(query.page(), query.size());
        var page = repository.search(toNamePattern(query.name()), query.active(), pageable);
        return new SupplierPage(
            page.getContent().stream()
                .map(SupplierMapper::toDomain)
                .map(SupplierResult::from)
                .toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
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
