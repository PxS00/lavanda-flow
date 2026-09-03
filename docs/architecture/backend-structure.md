# Spring Backend Structure

The Lavanda Flow backend follows **package by feature/domain** inside a **modular monolith** validated with Spring Modulith.

The structure must not be organized globally only by technical layer (`controller`, `service`, `repository`, `entity`). Each business module owns its rules, use cases, and adapters.

## Initial structure

```text
backend/
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/
    │   │       └── ceudelavanda/
    │   │           └── lavandaflow/
    │   │               ├── LavandaFlowApplication.java
    │   │               │
    │   │               ├── catalog/
    │   │               │   ├── domain/
    │   │               │   ├── application/
    │   │               │   └── infrastructure/
    │   │               │
    │   │               ├── inventory/
    │   │               │   ├── domain/
    │   │               │   ├── application/
    │   │               │   └── infrastructure/
    │   │               │
    │   │               ├── production/
    │   │               │   ├── domain/
    │   │               │   ├── application/
    │   │               │   └── infrastructure/
    │   │               │
    │   │               ├── suppliers/
    │   │               │   ├── domain/
    │   │               │   ├── application/
    │   │               │   └── infrastructure/
    │   │               │
    │   │               └── shared/
    │   │                   ├── config/
    │   │                   ├── error/
    │   │                   └── security/
    │   │
    │   └── resources/
    │       ├── application.yml
    │       ├── application-local.yml
    │       └── db/
    │           └── migration/
    │
    └── test/
        └── java/
            └── com/
                └── ceudelavanda/
                    └── lavandaflow/
                        ├── architecture/
                        ├── catalog/
                        ├── inventory/
                        └── suppliers/
```

## Internal module structure

Example for `inventory`:

```text
inventory/
├── domain/
│   ├── Batch.java
│   ├── StockMovement.java
│   ├── MovementType.java
│   ├── StockPolicy.java
│   ├── BatchRepository.java
│   ├── StockMovementRepository.java
│   └── exception/
│       ├── InsufficientStockException.java
│       └── InvalidStockMovementException.java
│
├── application/
│   ├── RegisterStockEntry.java
│   ├── RegisterStockWithdrawal.java
│   ├── AdjustStock.java
│   ├── ListExpiringBatches.java
│   ├── command/
│   │   ├── RegisterStockEntryCommand.java
│   │   └── RegisterStockWithdrawalCommand.java
│   └── result/
│       └── StockMovementResult.java
│
└── infrastructure/
    ├── web/
    │   ├── InventoryController.java
    │   ├── request/
    │   └── response/
    │
    └── persistence/
        ├── JpaBatchRepository.java
        ├── JpaStockMovementRepository.java
        ├── entity/
        └── mapper/
```

The names above are structural references. Do not create classes merely to fill the tree. Every artifact must exist because there is a use case or a concrete technical need.

## `domain`

Owns business rules and concepts.

It may contain:

- aggregates;
- domain entities;
- value objects;
- business enums;
- policies;
- domain services when a rule does not naturally belong to a single entity;
- repository interfaces required by the domain/application;
- business exceptions.

### Rules

- do not depend on controllers or HTTP DTOs;
- do not know JSON details;
- avoid direct dependency on technical implementations;
- rules such as negative stock prevention, FEFO, and expiration belong here or in appropriate application services, never in controllers.

## `application`

Owns module use-case execution.

Examples:

```text
RegisterStockEntry
RegisterStockWithdrawal
AdjustStock
ListExpiringBatches
```

### Responsibilities

- orchestrate domain objects;
- control transactional boundaries;
- access required ports/repositories;
- publish application/domain events when justified;
- return results appropriate to the calling adapter.

### Rules

- do not contain HTTP knowledge;
- avoid spreading complex domain rules across use cases;
- each use case should represent a clear business intention.

## `infrastructure`

Implements details external to the module core.

### `infrastructure/web`

Contains:

- REST controllers;
- request DTOs;
- response DTOs;
- mapping between HTTP and application layers.

A controller should:

1. receive the request;
2. validate the boundary;
3. transform it into a command/query;
4. call the use case;
5. return the HTTP response.

Controllers must not calculate stock, select FEFO batches, or mutate entities directly.

### `infrastructure/persistence`

Contains JPA/PostgreSQL details.

It may contain:

- repository adapters;
- JPA entities when the domain model is kept separate from persistence;
- mappers;
- technical specifications/queries.

The decision to separate domain entities from JPA entities should be pragmatic. Do not duplicate models without clear value, but do not let ORM convenience dictate domain rules either.

## Module `catalog`

Initial responsibilities:

```text
catalog
├── item registration
├── name and description
├── category
├── unit of measure
├── active/inactive state
└── classification information
```

Items may represent fragrance essences, chemical inputs, bases, packaging, or other controlled materials.

## Module `inventory`

Initial responsibilities:

```text
inventory
├── batches
├── initial quantity
├── current balance
├── expiration
├── entries
├── withdrawals
├── adjustments
├── movement history
├── FEFO
└── stock/expiration alerts
```

`inventory` may depend on the public API of `catalog`, but must not access internal module classes directly.

## Module `suppliers`

Initial responsibilities:

```text
suppliers
├── registration
├── identification
├── basic contact information
└── batch origin association
```

Do not turn this module into a CRM in V1.

## `shared`

Keep it small.

Allowed uses include truly cross-cutting infrastructure:

```text
shared/
├── config
├── error
└── security
```

Do not move business rules to `shared` simply because more than one module uses them. First evaluate ownership and public APIs between modules.

## Module `production`

ADR [0009](decisions/0009-define-v1-production-module-boundaries.md) adds one cohesive V1 production module:

```text
production
├── formula and recipe definitions
├── production executions
├── exact source-batch consumption records
├── internal production lot-code allocation
└── recursive genealogy relationships and queries
```

Do not create separate `formulas` or `traceability` modules for V1. `catalog` owns stable item metadata, including essence references. `inventory` continues to own batches, balances, movements, FEFO, expiration, and eligibility; it creates an internally produced output batch only through its public API when requested by the production use case.

## Dependencies between modules

Allowed direction:

```text
production ──► catalog
production ──► inventory
inventory ───► catalog
inventory ───► suppliers
```

The arrows mean “depends on.” `production` may depend on the public APIs of `catalog`, `inventory`, and narrowly scoped `shared` facilities. It must not depend directly on `suppliers`; supplier origin remains behind inventory's public batch view. `catalog`, `inventory`, and `suppliers` must not depend on `production`.

Production-facing public contracts must provide only the operations and immutable values needed to:

- resolve catalog item metadata, including stable essence references;
- inspect and validate source batches by stable identifier;
- atomically consume exact source-batch quantities and record movements;
- create exactly one internally produced output batch and its initial movement;
- return stable batch identifiers and the inventory facts needed by production and genealogy queries.

Cross-module contracts use stable identifiers and public value records. Direct cross-module JPA entity relationships, repository imports, and infrastructure imports are forbidden.

Dependencies must occur only through public module APIs.

Avoid:

```text
inventory -> catalog.infrastructure.persistence.*
```

Prefer:

```text
inventory -> catalog.<public API>
```

Spring Modulith must verify boundaries and cycles between modules.

## Events between modules

Do not introduce events merely to avoid simple Java calls.

The V1 production operation uses synchronous public-contract calls so production state, inventory effects, and genealogy share one local transaction. Do not replace this atomic path with messaging, sagas, distributed transactions, or eventual consistency without a new requirement and ADR.

## Transactions

As a rule, the transactional boundary belongs in the application use case.

A stock movement must persist atomically:

```text
validate operation
      ↓
update batch balance
      ↓
record stock_movement
      ↓
commit
```

If any step fails, the complete operation must roll back.

The `production` application use case owns the wider production transaction. Within it, the inventory public contract protects and validates source stock, creates consumption movements, and creates the output batch and its initial movement. Production persists the execution, exact consumption relationships, definitive generated lot code when requested, and genealogy in the same transaction. Any failure rolls back every effect.

## Java code documentation

Javadoc is the standard source-code documentation format for the Java backend.

Use Javadoc where it documents a durable contract or non-obvious behavior, especially for:

- public module APIs;
- repository/domain ports consumed outside their implementation package;
- domain types with important semantics or invariants;
- application services and use cases with meaningful preconditions, side effects, transaction semantics, or exceptional behavior;
- extension points intended for reuse;
- configuration contracts exposed through typed properties when semantics are not self-evident.

A useful Javadoc comment should explain **intent, contract, constraints, semantics, or rationale**. It should not merely translate the method name into prose.

Example:

```java
/**
 * Withdraws stock using FEFO allocation while preserving batch-level auditability.
 *
 * <p>The operation is atomic: batch balances and generated stock movements are committed
 * together. If the requested quantity cannot be fulfilled, no partial withdrawal is persisted.
 *
 * @param command validated withdrawal request
 * @return the movements generated by the withdrawal
 * @throws InsufficientStockException when available eligible stock is lower than requested
 */
public StockWithdrawalResult withdraw(RegisterStockWithdrawalCommand command) {
    // ...
}
```

Guidelines:

- use `@param`, `@return`, `@throws`, and `@since` when they add meaningful contract information;
- keep Javadoc synchronized when behavior changes;
- do not document trivial getters/setters or obvious private helpers;
- avoid comments that explain syntax or restate the implementation;
- prefer expressive names and types first, documentation second;
- inline comments are reserved for non-obvious rationale, constraints, or implementation trade-offs.

Documentation ownership by concern:

```text
Java contracts and code semantics -> Javadoc
HTTP/API contracts                -> OpenAPI / Swagger
Architecture decisions            -> ADRs
Development and operations        -> docs/ and CONTRIBUTING.md
```

When the backend bootstrap exists, Maven should be able to generate Javadoc as part of project documentation/verification without introducing runtime dependencies.

## Tests

The test structure should mirror production modules.

Example:

```text
src/test/java/com/ceudelavanda/lavandaflow/
├── architecture/
│   └── ModularityTest.java
├── catalog/
├── inventory/
├── production/
└── suppliers/
```

### Minimum architecture test

There must be a test that runs Spring Modulith module verification to detect:

- cycles;
- improper access to another module's internals;
- violations of allowed dependencies.

When `production` is implemented, its declared dependencies must be limited to the public APIs of `catalog`, `inventory`, and narrowly scoped `shared` facilities. Modulith tests must also prove that existing modules do not acquire a dependency on `production`, keeping the graph acyclic.

## Conventions

- base package: `com.ceudelavanda.lavandaflow`;
- class names and source code in English;
- engineering documentation in English;
- interfaces represent real contracts, not speculative abstractions;
- avoid the `Impl` suffix when a more specific adapter name can express responsibility;
- never expose JPA entities directly through the API;
- do not create a generic `util` layer that accumulates undefined responsibilities.
