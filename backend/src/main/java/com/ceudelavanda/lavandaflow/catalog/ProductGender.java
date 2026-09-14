package com.ceudelavanda.lavandaflow.catalog;

import java.util.Arrays;

/** Descriptive fragrance classification with stable source and transport codes. */
public enum ProductGender {
    MASCULINE("M"),
    FEMININE("F"),
    SHARED("C"),
    SHARED_MASCULINE("M/C"),
    SHARED_FEMININE("F/C");

    private final String code;

    ProductGender(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** Parses canonical codes only; source-specific normalization belongs to the importer. */
    public static ProductGender fromCode(String code) {
        return Arrays.stream(values()).filter(value -> value.code.equals(code)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("gender must be M, F, C, M/C or F/C"));
    }
}
