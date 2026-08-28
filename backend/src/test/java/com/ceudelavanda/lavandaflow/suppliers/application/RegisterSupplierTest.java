package com.ceudelavanda.lavandaflow.suppliers.application;

import com.ceudelavanda.lavandaflow.suppliers.domain.Supplier;
import com.ceudelavanda.lavandaflow.suppliers.domain.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterSupplierTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Test
    void shouldRegisterActiveSupplierUsingDomainNormalization() {
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var useCase = new RegisterSupplier(supplierRepository);

        var result = useCase.execute(new RegisterSupplierCommand(
            "  Issue94 Aromas Ltd  ", "  12.345.678/0001-90  ", "  compras@example.com  ", "   "
        ));

        assertThat(result.id()).isNotNull();
        assertThat(result.name()).isEqualTo("Issue94 Aromas Ltd");
        assertThat(result.identifier()).isEqualTo("12.345.678/0001-90");
        assertThat(result.contact()).isEqualTo("compras@example.com");
        assertThat(result.notes()).isNull();
        assertThat(result.active()).isTrue();
        verify(supplierRepository).save(any(Supplier.class));
    }

    @Test
    void shouldRejectBlankNameBeforePersistence() {
        var useCase = new RegisterSupplier(supplierRepository);

        assertThatThrownBy(() -> useCase.execute(new RegisterSupplierCommand("   ", null, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("name must not be blank");

        verify(supplierRepository, never()).save(any());
    }
}
