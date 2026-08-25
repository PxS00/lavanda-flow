package com.ceudelavanda.lavandaflow.shared.error;

/**
 * Classifies a domain error independently from its transport representation.
 */
public enum ErrorType {

    NOT_FOUND,
    VALIDATION,
    CONFLICT,
    BUSINESS_RULE
}
