# ADR 0007 — Standardize the API error contract

- **Status:** Accepted
- **Date:** 2026-08-25

## Context

The API needs a consistent error representation as modules and endpoints are added. HTTP concerns must remain in the web adapter, while domain exceptions need stable, machine-readable semantics that can also be used by adapters other than HTTP.

## Decision

All REST API errors use `ApiErrorResponse` with the following fields:

- a deterministic `timestamp`, obtained from an injected `Clock`;
- HTTP `status` and `error` values;
- a stable, machine-readable `code`;
- a human-readable `message`;
- the request `path`;
- optional field-level `details` for validation errors.

Domain exceptions expose a stable `code` and an `ErrorType` that is independent from Spring and HTTP. The global `@RestControllerAdvice` translates `ErrorType` to the appropriate HTTP status and builds the API error response.

The `shared.error` named interface is the only shared contract required by modules for this behavior. Controllers do not handle exceptions manually.

## Consequences

### Positive

- API clients receive one predictable error shape across modules;
- error codes remain stable for programmatic handling;
- domain code remains independent from HTTP and Spring;
- validation errors can report field-specific details;
- timestamps are deterministic in tests.

### Negative

- each new domain exception must select an appropriate `ErrorType`;
- the web adapter must maintain the mapping from semantic error types to HTTP statuses.

## Alternatives considered

### Store `HttpStatus` in domain exceptions

Rejected because it introduces a Spring HTTP dependency into domain code and reverses the intended architectural direction.

### Map error codes with string switches

Rejected because free-form strings are less safe and less expressive than a semantic error type.

### Handle exceptions in each controller

Rejected because it duplicates transport mapping and makes the API contract inconsistent between endpoints.
