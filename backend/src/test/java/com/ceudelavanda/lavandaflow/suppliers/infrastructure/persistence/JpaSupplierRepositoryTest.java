package com.ceudelavanda.lavandaflow.suppliers.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.suppliers.domain.Supplier;
import com.ceudelavanda.lavandaflow.suppliers.domain.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class JpaSupplierRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
        new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private SupplierRepository repository;

    @Test
    void shouldSaveAndFindSupplier() {
        var supplier = Supplier.create(
            "Lavanda Inputs",
            "12.345.678/0001-90",
            "contact@lavandainputs.example",
            "Preferred supplier"
        );

        repository.save(supplier);

        var persistedSupplier = repository.findById(supplier.getId());

        assertThat(persistedSupplier)
            .isPresent()
            .get()
            .satisfies(found -> {
                assertThat(found.getId()).isEqualTo(supplier.getId());
                assertThat(found.getName()).isEqualTo("Lavanda Inputs");
                assertThat(found.getIdentifier()).isEqualTo("12.345.678/0001-90");
                assertThat(found.getContact()).isEqualTo("contact@lavandainputs.example");
                assertThat(found.getNotes()).isEqualTo("Preferred supplier");
                assertThat(found.isActive()).isTrue();
            });
    }

    @Test
    void shouldPersistOptionalFieldsAndInactiveState() {
        var supplier = Supplier.create("Lavanda Inputs", null, null, null);
        supplier.deactivate();

        repository.save(supplier);

        var persistedSupplier = repository.findById(supplier.getId());

        assertThat(persistedSupplier)
            .isPresent()
            .get()
            .satisfies(found -> {
                assertThat(found.getIdentifier()).isNull();
                assertThat(found.getContact()).isNull();
                assertThat(found.getNotes()).isNull();
                assertThat(found.isActive()).isFalse();
            });
    }
}
