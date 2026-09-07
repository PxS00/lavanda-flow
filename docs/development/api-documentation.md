# API Documentation

Lavanda Flow publishes its Spring MVC HTTP contract with springdoc-openapi.

## Development endpoints

When the backend is running with the local profile, the documentation endpoints are:

```text
OpenAPI JSON:  /v3/api-docs
OpenAPI YAML:  /v3/api-docs.yaml
Swagger UI:    /swagger-ui.html
```

The generated specification is the primary source for HTTP contract discovery. Add explicit OpenAPI annotations only when inferred metadata is incomplete or when a contract detail cannot be expressed clearly by the Java types and Spring MVC mapping.

## Security

API documentation must never contain credentials, secrets, database connection details, tokens, or sensitive runtime configuration. Endpoint authentication and authorization requirements should be documented at the contract boundary as the security model is introduced.

## Versioning

The initial application API prefix is `/api/v1`. The OpenAPI document version identifies the public contract family and does not replace normal application release versioning.

## Exact decimal quantities

Inventory and production quantities use `BigDecimal` / PostgreSQL `NUMERIC(19,6)` and are represented in v0.5.0 HTTP JSON as plain decimal strings. For example:

```json
{ "quantity": "9999999999999.123456" }
```

The Angular client sends canonical quoted decimal values and consumes quoted decimal responses without JavaScript `number` conversion. The backend continues to accept representative legacy numeric request values where Jackson already supports them. Quantity responses intentionally changed from v0.4.0 JSON numbers to strings: the former representation could silently mutate values in browser clients. This is a corrective wire-contract change, not byte-compatible v0.4 response payload behavior.
