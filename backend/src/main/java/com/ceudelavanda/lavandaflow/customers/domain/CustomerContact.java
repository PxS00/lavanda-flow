package com.ceudelavanda.lavandaflow.customers.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Normalized contact input, validated by the application before any write.
 * Blank optional values become null. Only display separators are removed from phone values;
 * invalid characters remain visible to standard Bean Validation rather than being discarded.
 */
public record CustomerContact(
    @NotBlank @Size(max = 160) String name,
    @Pattern(regexp = "\\+?[0-9]{7,15}", message = "must contain an optional leading + and 7 to 15 digits") String phone,
    @Email @Size(max = 254) String email
) {
    public CustomerContact {
        name = name == null ? null : name.trim();
        phone = normalizeOptional(phone);
        if (phone != null) {
            phone = normalizePhoneSeparators(phone);
        }
        email = normalizeOptional(email);
    }

    /** Uses exactly the phone input separator rules, also for partial phone searches. */
    public static String normalizePhoneSeparators(String value) {
        return value.replaceAll("[ ().-]", "");
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
