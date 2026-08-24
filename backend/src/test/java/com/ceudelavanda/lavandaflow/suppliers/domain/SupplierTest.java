package com.ceudelavanda.lavandaflow.suppliers.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplierTest {

    @Test
    void shouldCreateActiveSupplier() {
        var supplier = Supplier.create(
            "Lavanda Inputs",
            "12.345.678/0001-90",
            "contact@lavandainputs.example",
            "Preferred supplier"
        );

        assertThat(supplier.getId()).isNotNull();
        assertThat(supplier.getName()).isEqualTo("Lavanda Inputs");
        assertThat(supplier.getIdentifier()).isEqualTo("12.345.678/0001-90");
        assertThat(supplier.getContact()).isEqualTo("contact@lavandainputs.example");
        assertThat(supplier.getNotes()).isEqualTo("Preferred supplier");
        assertThat(supplier.isActive()).isTrue();
    }

    @Test
    void shouldTrimFieldsAndNormalizeBlankOptionalValues() {
        var supplier = Supplier.create(
            "  Lavanda Inputs  ",
            "   ",
            "  contact@lavandainputs.example  ",
            null
        );

        assertThat(supplier.getName()).isEqualTo("Lavanda Inputs");
        assertThat(supplier.getIdentifier()).isNull();
        assertThat(supplier.getContact()).isEqualTo("contact@lavandainputs.example");
        assertThat(supplier.getNotes()).isNull();
    }

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> Supplier.create("   ", null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("name must not be blank");
    }

    @Test
    void shouldUpdateSupplierData() {
        var supplier = Supplier.create("Old Name", null, null, null);

        supplier.rename("New Name");
        supplier.changeIdentifier("identifier");
        supplier.changeContact("contact");
        supplier.changeNotes("notes");

        assertThat(supplier.getName()).isEqualTo("New Name");
        assertThat(supplier.getIdentifier()).isEqualTo("identifier");
        assertThat(supplier.getContact()).isEqualTo("contact");
        assertThat(supplier.getNotes()).isEqualTo("notes");
    }

    @Test
    void shouldDeactivateAndActivateSupplier() {
        var supplier = Supplier.create("Lavanda Inputs", null, null, null);

        supplier.deactivate();
        assertThat(supplier.isActive()).isFalse();

        supplier.activate();
        assertThat(supplier.isActive()).isTrue();
    }

    @Test
    void shouldRestoreSupplierWithExistingIdentifier() {
        var id = UUID.randomUUID();

        var supplier = new Supplier(
            id,
            "Lavanda Inputs",
            null,
            null,
            null,
            false
        );

        assertThat(supplier.getId()).isEqualTo(id);
        assertThat(supplier.isActive()).isFalse();
    }

    @Test
    void shouldRejectNullIdWhenRestoring() {
        assertThatThrownBy(() -> new Supplier(
            null,
            "Lavanda Inputs",
            null,
            null,
            null,
            true
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("id must not be null");
    }
}
