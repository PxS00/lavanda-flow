package com.ceudelavanda.lavandaflow.suppliers.application;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.suppliers.SupplierLookup;
import com.ceudelavanda.lavandaflow.suppliers.domain.Supplier;
import com.ceudelavanda.lavandaflow.suppliers.domain.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SupplierManagementIntegrationTest {

    @Autowired private RegisterSupplier registerSupplier;
    @Autowired private GetSupplier getSupplier;
    @Autowired private SearchSuppliers searchSuppliers;
    @Autowired private SupplierLookup supplierLookup;
    @Autowired private SupplierRepository supplierRepository;

    @Test
    void shouldRegisterRetrieveAndExposeSupplierLookup() {
        var registered = registerSupplier.execute(new RegisterSupplierCommand(
            "Issue94 Registered Supplier", "ID-94", "issue94@example.com", "Preferred V1 source"
        ));

        var retrieved = getSupplier.execute(registered.id());
        var snapshot = supplierLookup.findById(registered.id()).orElseThrow();

        assertThat(retrieved).isEqualTo(registered);
        assertThat(retrieved.active()).isTrue();
        assertThat(snapshot.id()).isEqualTo(registered.id());
        assertThat(snapshot.name()).isEqualTo("Issue94 Registered Supplier");
        assertThat(snapshot.active()).isTrue();
    }

    @Test
    void shouldFilterAndOrderSuppliersDeterministicallyWithoutInventingNameUniqueness() {
        var lowerId = new UUID(0, 101);
        var higherId = new UUID(0, 102);
        var inactiveId = new UUID(0, 103);
        supplierRepository.save(new Supplier(lowerId, "issue94 aroma house", null, null, null, true));
        supplierRepository.save(new Supplier(higherId, "Issue94 Aroma House", "DUPLICATE-NAME", null, null, true));
        supplierRepository.save(new Supplier(inactiveId, "Issue94 Inactive Source", null, null, null, false));

        var active = searchSuppliers.execute(new SupplierSearchQuery("issue94 aroma house", true, 0, 20));
        var inactive = searchSuppliers.execute(new SupplierSearchQuery("Issue94 Inactive Source", false, 0, 20));

        assertThat(active.content()).extracting(SupplierResult::id).containsExactly(lowerId, higherId);
        assertThat(active.totalElements()).isEqualTo(2);
        assertThat(inactive.content()).extracting(SupplierResult::id).containsExactly(inactiveId);
    }

    @Test
    void shouldTreatLikeWildcardsAsLiteralNameCharacters() {
        var percentId = new UUID(0, 104);
        var regularId = new UUID(0, 105);
        supplierRepository.save(new Supplier(percentId, "Issue94 Special % Supplier", null, null, null, true));
        supplierRepository.save(new Supplier(regularId, "Issue94 Special Regular Supplier", null, null, null, true));

        var result = searchSuppliers.execute(new SupplierSearchQuery("Issue94 Special %", null, 0, 20));

        assertThat(result.content()).extracting(SupplierResult::id).containsExactly(percentId);
    }
}
