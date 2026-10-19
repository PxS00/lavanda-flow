package com.ceudelavanda.lavandaflow.customers.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.customers.application.CustomerPage;
import com.ceudelavanda.lavandaflow.customers.application.CustomerQuery;
import com.ceudelavanda.lavandaflow.customers.application.CustomerResult;
import com.ceudelavanda.lavandaflow.customers.application.CustomerSearchQuery;
import com.ceudelavanda.lavandaflow.customers.domain.CustomerContact;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import java.util.Locale;

@Repository
@RequiredArgsConstructor
class JpaCustomerQuery implements CustomerQuery {
    private final SpringDataCustomerRepository repository;

    @Override
    public CustomerPage search(CustomerSearchQuery query) {
        var text = query.q();
        var phone = text == null ? null : CustomerContact.normalizePhoneSeparators(text);
        var page = repository.search(pattern(text), pattern(phone), query.active(), PageRequest.of(query.page(), query.size()));
        return new CustomerPage(page.getContent().stream().map(CustomerJpaEntity::toDomain)
            .map(CustomerResult::from).toList(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private static String pattern(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return "%" + value.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%")
            .replace("_", "!_") + "%";
    }
}
