package com.ceudelavanda.lavandaflow.suppliers.application;

import com.ceudelavanda.lavandaflow.suppliers.domain.Supplier;
import com.ceudelavanda.lavandaflow.suppliers.domain.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateSupplierTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Test
    void shouldUpdateSupportedFieldsAndPreserveSupplierIdentity() {
        var id = UUID.randomUUID();
        var supplier = new Supplier(
            id, "Old supplier", "OLD-ID", "old@example.test", "Old notes", true
        );
        when(supplierRepository.findById(id)).thenReturn(Optional.of(supplier));
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = new UpdateSupplier(supplierRepository).execute(new UpdateSupplierCommand(
            id,
            "  New supplier  ",
            "  NEW-ID  ",
            "  new@example.test  ",
            "  New notes  ",
            false
        ));

        assertThat(result).isEqualTo(new SupplierResult(
            id, "New supplier", "NEW-ID", "new@example.test", "New notes", false
        ));
        verify(supplierRepository).save(supplier);
    }

    @Test
    void shouldClearOptionalFieldsAndReactivateSupplier() {
        var id = UUID.randomUUID();
        var supplier = new Supplier(
            id, "Supplier", "ID", "contact@example.test", "Notes", false
        );
        when(supplierRepository.findById(id)).thenReturn(Optional.of(supplier));
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = new UpdateSupplier(supplierRepository).execute(new UpdateSupplierCommand(
            id, "Supplier", "  ", "", " ", true
        ));

        assertThat(result.identifier()).isNull();
        assertThat(result.contact()).isNull();
        assertThat(result.notes()).isNull();
        assertThat(result.active()).isTrue();
    }

    @Test
    void shouldReturnExistingNotFoundErrorWithoutSaving() {
        var id = UUID.randomUUID();
        when(supplierRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new UpdateSupplier(supplierRepository).execute(
            new UpdateSupplierCommand(id, "Supplier", null, null, null, true)
        )).isInstanceOf(SupplierNotFoundException.class);

        verify(supplierRepository, never()).save(any());
    }
}
