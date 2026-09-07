# Backend agent instructions

Apply the root `AGENTS.md` first. This file contains backend-specific guidance only.

## Stack and architecture

- Use Java 25, Spring Boot 4.1, Spring Modulith, Spring MVC, Spring Validation, Spring Data JPA, PostgreSQL, Flyway, Testcontainers, and Maven.
- Keep the modular monolith organized by feature/domain. The V1 modules are `catalog`, `inventory`, `production`, `suppliers`, and `shared`.
- Keep production cohesive: `production` owns formulas/recipes, the production lifecycle and transaction orchestration, lot-code allocation, consumption records, and genealogy. Do not create separate `formulas` or `traceability` modules.
- `inventory` retains ownership of `Batch` and all stock invariants; `catalog` retains stable item metadata, including essence references and production-type metadata.
- `production` may depend only on public APIs of `catalog`, `inventory`, and narrowly scoped `shared` facilities. Existing modules must not depend on `production`.
- Cross module boundaries only through public APIs and stable identifiers or immutable values; never import another module's internal infrastructure or JPA entities.
- Do not use a global `controller`/`service`/`repository`/`entity` package layout.
- Controllers do not own business rules. Do not add speculative patterns, modules, events, or abstractions.

## Domain

- Quantities use `BigDecimal`; stock cannot become negative.
- Movements are immutable and auditable. Corrections create new adjustment movements.
- Balance-changing operations are transactional.
- Production state and inventory effects must commit atomically through public module APIs.
- FEFO and expiration behavior are backend-authoritative. `expiresAt <= today` is expired.
- Use the application `Clock` for date-sensitive behavior and tests.

## HTTP API

- Use DTOs at HTTP boundaries; never expose JPA entities.
- Keep controllers thin, validate input, and preserve existing routes, contracts, and wire values.
- Follow `/api/v1` conventions where already established and keep error contracts consistent.
- Use OpenAPI where useful; avoid annotations whose contract is already inferred.

## Persistence

- PostgreSQL is the source of truth. Flyway controls schema evolution; do not rely on `ddl-auto`.
- Enforce important invariants with database constraints where appropriate.
- Integration tests use PostgreSQL through Testcontainers, not H2.

## Spring and Java practices

- Use only the approved dependencies in `docs/architecture/dependencies.md`.
- Use Lombok deliberately: prefer constructor injection where appropriate, avoid indiscriminate `@Data` on JPA or domain entities, and do not weaken encapsulation or invariants.
- Prefer typed `@ConfigurationProperties` for applicable configuration groups.
- Write Javadoc for meaningful public contracts, domain semantics, application-service contracts, extension points, preconditions, side effects, transactional behavior, and non-obvious invariants. Avoid boilerplate.

## Testing and validation

Prioritize stock balance, FEFO, expiration, concurrency, quantity validation, API contracts, and module-boundary tests. Do not weaken production code to make tests easier.

Run `./mvnw verify` for backend changes.
