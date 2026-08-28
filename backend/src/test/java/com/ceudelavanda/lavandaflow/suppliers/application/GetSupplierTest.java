package com.ceudelavanda.lavandaflow.suppliers.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSupplierTest {

    @Mock
    private SupplierQuery supplierQuery;

    @Test
    void shouldReturnSupplier() {
        var supplierId = UUID.randomUUID();
        var expected = new SupplierResult(supplierId, "Issue94 Aromas", null, null, null, true);
        when(supplierQuery.findById(supplierId)).thenReturn(Optional.of(expected));

        assertThat(new GetSupplier(supplierQuery).execute(supplierId)).isEqualTo(expected);
    }

    @Test
    void shouldThrowStandardizedNotFoundError() {
        var supplierId = UUID.randomUUID();
        when(supplierQuery.findById(supplierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new GetSupplier(supplierQuery).execute(supplierId))
            .isInstanceOf(SupplierNotFoundException.class)
            .satisfies(exception -> {
                var notFound = (SupplierNotFoundException) exception;
                assertThat(notFound.getCode()).isEqualTo("SUPPLIER_NOT_FOUND");
                assertThat(notFound.getDetails()).containsEntry("supplierId", supplierId.toString());
            });
    }
}
