# ADR 0009 — Define V1 production module boundaries

- **Status:** Accepted
- **Date:** 2026-09-02

## Context

V1 now includes minimum formula definitions, internal production registration, exact source-batch consumption, internally produced batches, internal lot-code allocation, and recursive bidirectional genealogy.

The existing modules have established responsibilities and dependency patterns:

- `catalog` owns inventory-item identity, classification, unit, and active state and publishes immutable lookup values;
- `inventory` owns `Batch`, balances, movements, FEFO, expiration, eligibility, and stock locking;
- `suppliers` owns supplier data and publishes an immutable lookup contract;
- cross-module references are UUIDs and public values rather than imported JPA entities;
- `inventory` already depends synchronously on public `catalog` and `suppliers` contracts for atomic stock use cases.

Production adds a cohesive lifecycle that is not merely another stock movement: a formula describes required items, an execution records actual allocations, one execution creates one output batch, and those relations form recursive genealogy. The complete operation must nevertheless share one transaction with inventory effects.

## Decision

Create one Spring Modulith module named `production`. Do not create separate `formulas` or `traceability` modules for V1.

### Ownership

`production` owns:

- minimum formula and recipe definitions;
- production executions;
- exact source-batch consumption records and quantities;
- generated internal lot-code allocation, including monthly sequence uniqueness;
- explicit output-batch/source-batch genealogy relationships;
- recursive upstream and downstream genealogy query orchestration.

`catalog` owns stable metadata that describes an inventory item rather than a production execution. This includes the stable essence reference used by `EEE`, with `000` representing no essence, and any stable product-type metadata needed to obtain `TTT`. An essence remains an inventory item; no duplicate essence aggregate is introduced in `production`.

`inventory` continues to own the inventory batch, its balance, movements, FEFO, expiration, eligibility, and stock concurrency controls. It creates and persists an internally produced output batch, plus its stock-creating movement, when requested through its public API by `production`.

### Public contracts

The implementation must add only the public module capabilities needed for the approved use cases. Their exact Java type names are deferred, but their responsibilities are fixed:

- a catalog query provides stable item identifiers, active state, unit, stable essence reference, and production-type metadata needed for formula validation and lot allocation;
- inventory queries expose source-batch identity and the backend-authoritative facts needed to validate availability, expiration, FEFO, and eligibility;
- one inventory production command accepts exact source-batch allocations and output-batch data, applies all inventory effects atomically, and returns stable identifiers and immutable results needed by the production record;
- inventory's implementation keeps batch repositories, movement repositories, locking, and JPA types internal.

These are application/module contracts, not REST contracts. This ADR does not define endpoints, DTOs, class names, or wire formats.

### Batch references and persistence boundaries

Production-owned records refer to catalog items and inventory batches by stable identifiers carried through public contracts. `production` must not import catalog or inventory domain objects, repositories, JPA entities, mappers, or infrastructure packages.

Direct cross-module JPA entity relationships are not permitted. Production persistence stores identifier references and maps only production-owned state. Database constraints may enforce cross-module referential integrity when the implementation schema is designed, but this ADR does not select tables, columns, keys, or constraints.

An inventory batch may retain an opaque production-execution identifier as origin metadata without creating an inventory-to-production code dependency. Genealogy remains production-owned and is derived from explicit execution, output-batch, and consumption references, never from lot codes or notes.

## Module dependency direction

The allowed graph is:

```text
production ──► catalog
production ──► inventory ──► catalog
                         └──► suppliers
catalog ─────► shared
inventory ───► shared
production ──► shared
suppliers ───► shared
```

Arrows mean “depends on.” `production` has no direct dependency on `suppliers`; it consumes supplier-origin facts through inventory's public batch representation. `catalog`, `inventory`, and `suppliers` do not depend on `production`. This keeps the graph acyclic even when an inventory batch carries a production-execution identifier as data.

## Transaction boundary

The production application use case owns one local PostgreSQL transaction for the complete business operation. It validates the formula and catalog metadata, allocates the definitive internal lot code when automatic generation is selected, and synchronously calls inventory's public production command within the same transaction.

Inventory remains responsible for its own invariants inside that call: it protects and validates the concrete source batches, rejects ineligible or insufficient stock, reduces exact `BigDecimal` quantities, records immutable consumption movements, and creates exactly one output batch with its initial movement. Production then persists the execution, exact consumption records, output-batch reference, lot allocation, and genealogy before the shared transaction commits.

Any failure rolls back production state, source-batch balances, movements, output-batch creation, lot allocation state, and genealogy. The implementation must preserve the existing application `Clock`, backend-authoritative FEFO and eligibility rules, and inventory locking decisions. No distributed transaction, saga, event-driven continuation, or eventual consistency is introduced.

## Testing implications

The existing Spring Modulith `ApplicationModules.verify()` test remains the baseline. Production implementation must declare only `catalog`, `inventory`, and narrowly scoped `shared` dependencies and must not expose internal packages as named interfaces merely for convenience.

Tests added with implementation must verify:

- the module graph is acyclic and existing modules do not depend on `production`;
- production uses only catalog and inventory public APIs;
- no cross-module infrastructure or JPA type is imported;
- the complete production operation commits or rolls back atomically;
- concurrent stock and generated-code operations preserve their invariants;
- recursive genealogy works across arbitrary production depth.

## Alternatives considered

### Keep production inside `inventory`

Rejected. It would minimize module count, but formula lifecycle, production execution, lot allocation, and genealogy are a cohesive production responsibility rather than inventory balance management. Placing them in `inventory` would enlarge that module around a second business lifecycle and make future maintenance less clear. Transaction convenience is not sufficient reason to merge the boundaries because the modular monolith supports one transaction across synchronous module application services.

### One cohesive `production` module

Accepted. It is the smallest boundary that keeps production concepts together while preserving inventory's existing ownership. It requires a narrow inventory public command but no duplicated stock model, asynchronous coordination, or reverse dependency.

### Separate `formulas`, `production`, and `traceability` modules

Rejected. V1 has no independent lifecycle, deployment, team, or consistency requirement for these concepts. Splitting them would add contracts and coordination around one atomic workflow and make cycles more likely without a concrete benefit.

## Consequences

### Positive

- production has one cohesive owner;
- inventory keeps its established invariants and persistence internals;
- formulas and genealogy do not become speculative modules;
- synchronous calls preserve one atomic PostgreSQL transaction;
- identifier references avoid cross-module ORM coupling;
- the dependency graph stays explicit and acyclic.

### Trade-offs

- the production use case coordinates work owned by two modules;
- inventory needs a production-facing public application contract;
- genealogy queries may combine production-owned relations with inventory and catalog public views;
- identifier references require explicit lookup and validation rather than ORM navigation.

These trade-offs are preferable to putting a second lifecycle inside inventory or fragmenting one V1 workflow across three modules.

## Deferred decisions

Implementation issues must decide exact Java names, command/result shapes, persistence entities, Flyway schema, database constraints, locking mechanics for generated sequences, REST contracts, authorization, and UI structure. This ADR does not change existing APIs or data and requires no compatibility migration; changing this boundary later requires a superseding ADR and an explicit migration plan if production data already exists.
