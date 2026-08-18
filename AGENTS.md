# AGENTS.md

## Project

Lavanda Flow is an inventory and production management system for Céu de Lavanda.

V1 focuses on general inventory, batches, stock movements, expiration dates, suppliers, and alerts. Formulas, automated production, cost calculation, and complete traceability are later evolutions.

Before implementing any functionality, read the relevant documentation:

- `docs/product/scope-v1.md`
- `docs/domain/domain-model.md`
- `docs/architecture/architecture.md`
- `docs/architecture/data-model.md`
- `docs/architecture/dependencies.md`
- `docs/architecture/backend-structure.md`
- `docs/development/git-workflow.md`
- `CONTRIBUTING.md`

## Official stack

### Frontend

- Angular 22
- TypeScript
- Angular Router
- Reactive Forms
- Signals
- Angular Material / CDK
- Vitest + jsdom
- angular-eslint + ESLint flat config

### Backend

- Java 25 LTS
- Spring Boot 4.1
- Spring Modulith
- Spring Web
- Spring Validation
- Spring Security
- Spring Data JPA
- Flyway
- PostgreSQL JDBC Driver
- Lombok
- springdoc-openapi / Swagger UI
- Spring Boot Actuator
- Micrometer Prometheus Registry
- Spring Boot DevTools
- Spring Configuration Processor
- Spring Boot Docker Compose Support

### Data and infrastructure

- PostgreSQL
- Docker
- Docker Compose
- GitHub Actions

### Testing

- JUnit 5
- Mockito
- Spring Boot Test
- Spring Security Test
- Spring Modulith Test
- Testcontainers with PostgreSQL
- Vitest on the frontend

The approved dependency list and deliberately excluded bootstrap dependencies are documented in `docs/architecture/dependencies.md`.

## Architecture principles

- modular monolith
- package by feature/domain
- explicit module boundaries
- business rules outside controllers
- infrastructure details must not dominate the domain model
- avoid microservices without a concrete need
- avoid speculative abstractions

## Backend

### Organization

The complete reference is in `docs/architecture/backend-structure.md`.

Base package:

```text
com.ceudelavanda.lavandaflow
```

Primary structure:

```text
com.ceudelavanda.lavandaflow
├── catalog/
│   ├── domain/
│   ├── application/
│   └── infrastructure/
├── inventory/
│   ├── domain/
│   ├── application/
│   └── infrastructure/
├── suppliers/
│   ├── domain/
│   ├── application/
│   └── infrastructure/
└── shared/
```

Do not use a global technical-layer structure such as:

```text
controller/
service/
repository/
entity/
```

Do not create future modules (`formulas`, `production`, `traceability`) before they enter the approved scope.

### Module dependencies

- access other modules only through public APIs
- never import another module's infrastructure internals
- avoid cycles
- use Spring Modulith to verify boundaries
- introduce events only when there is a business or architectural reason

### API

- use DTOs at HTTP boundaries
- never expose JPA entities directly
- validate inputs
- keep REST contracts consistent
- use `/api/v1` as the initial API prefix
- keep controllers thin
- publish OpenAPI and Swagger UI contracts
- document relevant response codes, authentication, and errors
- avoid redundant OpenAPI annotations when the contract is already inferred correctly

### Domain

- physical quantities use `BigDecimal`
- never use `double` or `float` for inventory quantities
- stock movements must be auditable
- negative stock is forbidden
- corrections create adjustment movements instead of destructive history edits
- use FEFO when applicable
- balance-changing operations must be transactional

### Persistence

- PostgreSQL is the source of truth
- schema evolution uses Flyway
- do not rely on `ddl-auto` for controlled schema evolution
- important invariants should also be enforced with database constraints when appropriate
- do not use H2 as the primary PostgreSQL substitute in integration tests

### Backend dependencies

Bootstrap from the approved set in `docs/architecture/dependencies.md`.

Lombok is approved with controlled usage:

- prefer `@RequiredArgsConstructor` for constructor injection
- use `@Getter`, `@Setter`, `@Builder`, and `@Slf4j` only when they improve clarity
- avoid `@Data` on JPA entities and domain aggregates
- do not generate `equals/hashCode/toString` indiscriminately on JPA entities
- Lombok must not weaken encapsulation or hide invariants

Configuration and local development:

- prefer `@ConfigurationProperties` for typed configuration groups
- keep `spring-boot-configuration-processor` enabled for metadata and autocomplete
- use `spring-boot-docker-compose` only to improve local development
- production behavior must not depend on Spring Boot Docker Compose lifecycle support
- DevTools is development-only

Initial observability:

- use Spring Boot Actuator for health and metrics
- keep `micrometer-registry-prometheus` as the approved metrics registry
- expose only required management endpoints
- do not add distributed tracing/OpenTelemetry without a concrete requirement

Do not add initially without justification:

- MapStruct
- Redis
- Kafka / RabbitMQ
- H2
- Resilience4j
- OpenTelemetry / distributed tracing
- additional state, cache, or messaging libraries

Use Spring BOM/parent dependency management whenever possible.

### Tests

Every relevant business rule requires tests.

Prioritize:

- stock balance
- FEFO
- expiration rules
- concurrent stock movements
- quantity validation
- primary API contracts
- Spring Modulith boundaries

Use Testcontainers with PostgreSQL for integration tests.

## Frontend

- keep Angular decoupled from the backend through REST contracts
- prioritize mobile-first UX
- keep components small and responsibility-oriented
- keep HTTP access logic outside presentation components
- use Angular `HttpClient`, not Axios
- use Reactive Forms as the V1 default
- use Signals for local and derived state when appropriate
- use RxJS when asynchronous composition justifies it
- handle loading, error, and empty states explicitly
- Angular Material is the initial design system
- configure `angular-eslint` with ESLint flat config
- standard frontend validation is `ng lint`, `ng test`, and `ng build`
- do not use `@angular/animations` for new code; prefer CSS and `animate.enter` / `animate.leave` when needed
- avoid libraries that duplicate Angular capabilities

Do not add initially without a concrete need:

- NgRx
- Axios
- external form libraries
- external routing libraries
- Tailwind alongside Angular Material
- `@angular/animations` for new implementations

PWA support is planned but is not part of the initial bootstrap requirement.

## Security

- never commit secrets
- never store plaintext passwords
- validate backend payloads
- configure CORS explicitly
- do not log credentials or sensitive data
- follow the principle of least privilege

## Quality

Prioritize:

- Clean Code
- SOLID when applicable
- explicit names
- small methods
- low duplication
- testable business rules
- simplicity over unnecessary patterns

Do not introduce design patterns only to increase abstraction.

## Git workflow

The authoritative policy is `docs/development/git-workflow.md`.

Once issue-driven development starts:

- `main` is production-only
- `develop` is the integration branch for the next release
- normal work starts from a GitHub issue
- feature branches use `features/<issue-number>/<short-description>`
- feature branches start from `develop`
- feature PRs target `develop`
- feature PRs use squash merge
- releases use `release/vX.Y.Z`
- release branches start from `develop`
- release PRs target `main`
- release PRs use a regular merge commit
- emergency production fixes use `hotfix/<issue-number>/<short-description>` and must be synchronized back into `develop`

Direct commits to `main` are allowed only during the initial documentation, governance, and bootstrap phase. This exception ends when issue-driven functional development begins and `develop` is established.

## Commit policy

Use Conventional Commits in English.

Examples:

```text
feat(inventory): add inventory item registration
fix(inventory): prevent negative stock balance
test(inventory): cover FEFO batch selection
refactor(catalog): isolate item classification policy
docs: define inventory domain model
chore(build): configure local postgres
ci: verify backend build
```

Commits must be atomic by meaningful checkpoint.

Do not create a commit for every trivial edit, but also do not accumulate thousands of unrelated modifications into one oversized commit. A commit should represent one coherent development checkpoint that can be understood and reviewed independently.

## Language policy

Engineering artifacts are written in English:

- branch names
- commit messages
- issue titles and bodies
- pull request titles and descriptions
- source code identifiers
- code comments when necessary
- architecture and development documentation
- ADRs
- API documentation
- CI/CD and operational documentation

User-facing application content may remain in Portuguese.

Existing Portuguese documentation should be migrated to English as it is revised and preferably before functional development becomes substantial.

## Scope

Do not implement functionality outside V1 only because the architecture anticipates it.

Before adding formulas, production, costs, or complete traceability, the corresponding scope must be approved and documented.

## Rules for agents

Before changing code:

1. read the relevant documentation
2. identify the responsible module
3. confirm that required dependencies are approved
4. preserve inventory invariants
5. keep the change inside the linked issue scope
6. implement the smallest complete solution
7. create coherent checkpoint commits rather than trivial or oversized commits
8. add or update tests
9. run the relevant build, lint, and test commands
10. review `git diff` before finalizing

If a structural decision is undocumented and may affect multiple parts of the system, do not assume silently. Propose or record an architectural decision first.
