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
