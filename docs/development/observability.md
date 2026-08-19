# Backend Observability

Lavanda Flow starts with Spring Boot Actuator and Micrometer's Prometheus registry. Distributed tracing is intentionally outside the Foundation scope.

## Management endpoints

The approved exposed management endpoints are:

```text
/actuator/health
/actuator/info
/actuator/prometheus
```

The configured exposure list is deliberately small. New management endpoints must be enabled only when they have a concrete operational use case.

## Health information

Health details use `when-authorized`. Public health responses must not expose credentials, connection strings, environment variables, internal topology, or other sensitive configuration.

## Metrics

The Prometheus endpoint exposes metrics registered by Spring Boot and Micrometer, including JVM and HTTP metrics and database-pool metrics when the corresponding instrumentation is active.

Application-specific metrics should use stable names and low-cardinality tags. Do not use user IDs, request payloads, free-form error messages, or other unbounded values as metric labels.

## Tracing

OpenTelemetry and distributed tracing are not part of the initial observability baseline. Add tracing only after a documented operational requirement justifies the additional complexity.
