# Architecture

## Architectural style

Lavanda Flow is a modular monolith. This keeps deployment and operations simple while preserving clear domain responsibilities and boundaries. Microservices are not part of the initial architecture.

The backend is organized by feature or domain, not by global technical layers. Spring Modulith verifies module boundaries; one module communicates with another only through public APIs and never imports another module's internal infrastructure.

## Technology stack

- **Frontend:** Angular 22, TypeScript, Angular Router, Signals, Reactive Forms, and Angular's native HTTP client;
- **Backend:** Java 25 LTS, Spring Boot 4.1, Spring Modulith, Spring Web, Validation, Data JPA, Security, and Flyway;
- **Data:** PostgreSQL;
- **Tests:** JUnit 5, Mockito, and Testcontainers;
- **Infrastructure:** Docker, Docker Compose, and GitHub Actions.

The interface is mobile-first and may evolve into a PWA when operational requirements justify it.

## High-level view

```text
┌─────────────────────────────┐
│          Angular 22         │
│        Web / Mobile         │
└──────────────┬──────────────┘
               │ HTTPS / REST / JSON
               ▼
┌─────────────────────────────┐
│       Spring Boot 4.1       │
│        Java 25 LTS          │
│                             │
│      Modular Monolith       │
├─────────────────────────────┤
│ catalog                     │
│ inventory                   │
│ suppliers                   │
│ shared                      │
│                             │
│ V1 capabilities also cover │
│ formulas, production, and   │
│ recursive traceability      │
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────┐
│         PostgreSQL          │
│          Flyway             │
└─────────────────────────────┘
```

## Current module boundaries

The current Spring Modulith modules remain:

- `catalog`: item registration, classification, unit of measure, and active state;
- `inventory`: batches, balances, movements, FEFO, expiration, and stock alerts;
- `suppliers`: supplier registration and association with externally supplied batches;
- `shared`: strictly cross-cutting configuration, errors, and security concerns.

Formula, production, and recursive traceability are approved V1 capabilities, but this product decision does not establish new modules. Their concrete ownership and interactions require implementation specifications and, if module boundaries materially change, an ADR. Do not create speculative `formulas`, `production`, or `traceability` modules solely because the concepts now belong to V1.

The package-by-feature convention, internal `domain`/`application`/`infrastructure` responsibilities, and current dependency rules remain defined in `backend-structure.md`.

## V1 production architecture requirements

The eventual implementation must preserve one inventory and batch model for raw materials (`matéria-prima`), intermediate products (`produto intermediário`), and finalized products (`produto finalizado`). An inventory batch may be externally supplied or produced internally; external manufacturer or supplier lot codes are preserved.

A formula describes required inventory items and proportions. A production execution records the concrete source batches and quantities actually consumed and creates exactly one distinct output batch. Produced intermediate batches can be consumed by later executions, so explicit relationships must support recursive genealogy at arbitrary depth in both upstream and downstream directions.

Lot codes are human operational identifiers, not database identities or genealogy storage. Genealogy must come from explicit production, consumption, source-batch, and output-batch relationships.

## Application and transaction boundaries

Application use cases orchestrate domain behavior and own transaction boundaries. Business rules such as negative-stock prevention, FEFO, expiration eligibility, source-batch allocation, production atomicity, and generated lot-code sequencing do not belong in controllers or frontend components.

Registering production is one atomic business operation:

```text
validate formula and concrete source allocations
                    ↓
lock or otherwise protect affected stock and generated sequence
                    ↓
record source-batch consumption and stock movements
                    ↓
create the one output batch and its stock history
                    ↓
persist explicit genealogy relationships
                    ↓
commit all or roll back all
```

Concurrent operations must not produce negative balances, lost updates, duplicate generated internal lot codes, or partial production state. The implementation specification must select the concrete concurrency strategy; this document does not prescribe optimistic versus pessimistic locking or a physical sequence design.

## API boundaries

Frontend/backend communication uses REST over JSON, initially versioned under `/api/v1`. HTTP boundaries use specific DTOs and input validation, return consistent errors, and never expose JPA entities directly. This scope decision does not define production routes, DTOs, wire values, or authorization rules.

The frontend may recommend or preview an automatically generated internal lot code, but it cannot reserve or authoritatively calculate the next sequence. The backend assigns the definitive code only when production succeeds. Explicit manual lot-code entry remains an allowed future UI path.

## Persistence and consistency

- PostgreSQL is the source of truth;
- Flyway is the only supported schema-evolution mechanism in controlled environments;
- Hibernate does not modify production schemas automatically;
- quantities use `BigDecimal` in the backend and `NUMERIC`/`DECIMAL` in PostgreSQL;
- every stock change has immutable, auditable movement history;
- stock balance and movement persistence occur atomically;
- production consumption, output creation, and genealogy persistence occur atomically;
- FEFO, expiration, available stock, and consumption eligibility remain backend-authoritative;
- date-dependent rules use the application `Clock`.

Database constraints should enforce integrity where applicable. Exact production tables, columns, indexes, foreign keys, and sequence-allocation mechanics remain implementation decisions.

## Security and observability

V1 requires authentication before public exposure, secure password storage, externalized secrets, least privilege, validated payloads, explicit CORS, and logs without sensitive data. A detailed production authorization model is not decided by this issue.

Initial observability consists of useful structured logs, health checks, and distinguishable business and infrastructure errors. Distributed tracing is not a V1 priority for this monolith.

## Testing strategy

- unit tests cover domain rules such as balances, FEFO, expiration, quantities, production atomicity, and lot-code allocation;
- integration tests use PostgreSQL through Testcontainers for persistence, transactions, constraints, concurrency, and recursive genealogy;
- API tests cover approved contracts and error cases;
- Spring Modulith tests verify boundaries and cycles.

## Deliberately open implementation decisions

Future implementation issues or ADRs must decide, when required:

- whether formula, production, and traceability responsibilities fit current modules or justify new module boundaries;
- exact REST endpoints, DTOs, and authorization rules;
- persistence entities and physical schema details;
- concurrency and automatic lot-sequence allocation mechanisms;
- UI component structure.

Automatic unit conversion, speculative events, and unrelated ERP capabilities are not implied by the approved production scope.
