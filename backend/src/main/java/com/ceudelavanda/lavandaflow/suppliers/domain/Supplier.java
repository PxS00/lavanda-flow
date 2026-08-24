package com.ceudelavanda.lavandaflow.suppliers.domain;

import lombok.Getter;

import java.util.UUID;

/**
 * Represents a commercial source that can be associated with inventory batches.
 *
 * <p>The supplier keeps only the basic registration data needed by V1 and is
 * deliberately not modeled as a customer relationship management record.</p>
 */
@Getter
public class Supplier {

    private final UUID id;
    private String name;
    private String identifier;
    private String contact;
    private String notes;
    private boolean active;

    public Supplier(
        UUID id,
        String name,
        String identifier,
        String contact,
        String notes,
        boolean active
    ) {
        this.id = requireId(id);
        this.name = requireName(name);
        this.identifier = normalizeOptional(identifier);
        this.contact = normalizeOptional(contact);
        this.notes = normalizeOptional(notes);
        this.active = active;
    }

    public static Supplier create(
        String name,
        String identifier,
        String contact,
        String notes
    ) {
        return new Supplier(
            UUID.randomUUID(),
            name,
            identifier,
            contact,
            notes,
            true
        );
    }

    public void rename(String name) {
        this.name = requireName(name);
    }

    public void changeIdentifier(String identifier) {
        this.identifier = normalizeOptional(identifier);
    }

    public void changeContact(String contact) {
        this.contact = normalizeOptional(contact);
    }

    public void changeNotes(String notes) {
        this.notes = normalizeOptional(notes);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    private static UUID requireId(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }

        return id;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }

        return name.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }

        var normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
