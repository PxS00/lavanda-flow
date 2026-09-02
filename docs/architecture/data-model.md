# Data Model

## Purpose

This document describes the V1 relational model and the minimum conceptual relationships required for inventory, production, and recursive batch genealogy. Established inventory structures remain concrete; new production structures stay conceptual until an implementation issue or ADR decides their physical design.

PostgreSQL is the source of truth, and Flyway controls every schema change.

## Inventory model

```text
supplier
   │ 1:N (external origin only)
   ▼
inventory_batch ◄── N:1 ── inventory_item
   │
   │ 1:N
   ▼
stock_movement
```

### `inventory_item`

Represents any stock-controlled item: raw material (`matéria-prima`), intermediate product (`produto intermediário`), finalized product (`produto finalizado`), packaging, or another operational item.

Established fields include identity, name, category, default unit, optional minimum stock, active state, optional notes, and audit timestamps. Name, category, and default unit are required; minimum stock cannot be negative.

### `supplier`

Represents an external commercial source with identity, name, optional identifier and contact details, active state, and audit timestamps.

### `inventory_batch`

Represents identifiable physical stock for one inventory item. Existing inventory principles include identity, item relationship, optional supplier relationship, lot code, initial and current quantities, relevant dates, expiration, optional notes, audit timestamps, and concurrency version.

Every batch has one conceptual origin:

- **external:** associated with a supplier or manufacturer when known and preserving the lot code assigned by that source; or
- **internal:** the one output batch of a Céu de Lavanda production execution.

These origins do not create separate batch or stock systems. A produced intermediate batch is an ordinary `inventory_batch` and may later be consumed by production.

`current_quantity` may remain a materialized balance for efficient queries only when every update is accompanied by `stock_movement` in the same transaction. It is never negative. Externally supplied lot codes are not automatically replaced by internal lot codes.

### `stock_movement`

Represents an immutable, auditable quantity change for one inventory batch. Established movement types include `ENTRY`, `CONSUMPTION`, `ADJUSTMENT_IN`, `ADJUSTMENT_OUT`, `LOSS`, and `EXPIRED_DISPOSAL`.

A movement has a positive exact-decimal quantity and required occurrence time. Corrections create new movements rather than changing historical records.

## Minimum production relationships

The physical table and column names below the established inventory model are deliberately unspecified. The relational design must represent at least these concepts and cardinalities:

```text
Formula / recipe
   │ 1:N
   ▼
Formula requirement ── N:1 ── Inventory item

Formula / recipe
   │ 1:N
   ▼
Production execution ── exactly 1 output ──► Inventory batch
   │
   │ 1:N
   ▼
Production consumption ── N:1 ──► source Inventory batch
          │
          └── exact quantity consumed
```

The required semantics are:

- formula or recipe requirements identify required inventory items and proportions;
- a production execution references the applicable formula or recipe and records the produced item and quantity;
- each production execution creates exactly one new output `inventory_batch`;
- each internally produced batch is the output of exactly one production execution;
- repeated executions create distinct output batches even with identical product, formula, source batches, and month;
- each production consumption joins one execution to one concrete source batch and records the exact positive quantity consumed;
- one execution may contain multiple consumptions for different source batches of the same inventory item;
- a source batch may be external or internally produced;
- one source batch may be consumed by multiple production executions, subject to available stock.

Formula requirements and production consumptions are separate relationships: the former describes what should be used; the latter records what was actually allocated.

## Recursive bidirectional genealogy

Genealogy is derived from explicit production-execution, output-batch, and source-batch-consumption relations:

- **upstream:** output batch -> producing execution -> source batches -> their producing executions -> earlier source batches;
- **downstream:** source batch -> consuming executions -> output batches -> later consuming executions -> descendants.

Because an internally produced batch may itself be consumed, the same relationship supports arbitrary depth without special raw-material, base, or finalized-product tables. Genealogy must not be inferred from `lot_code`, notes, categories, or naming conventions.

## Transaction and integrity requirements

Production registration commits as one atomic business operation. The transaction must include:

- validation of source-batch eligibility and sufficient balance;
- all source-batch balance reductions;
- auditable consumption movements;
- exact production-consumption records;
- creation of the one output batch and its stock-creating history;
- its relationship to the production execution;
- definitive generated internal lot-code allocation when automatic generation is chosen.

Failure rolls back every effect; no negative balance or partial production state may remain. Concurrent withdrawals and productions must not overspend stock, and concurrent automatic lot allocation must not produce duplicate codes.

Quantities use `NUMERIC`/`DECIMAL`, never floating-point types. FEFO, expiration, available stock, and inventory eligibility are backend-authoritative. Date-dependent rules use the application `Clock`; `expiresAt <= today` is expired.

## Internal lot-code data semantics

Internally produced batches use `TTT-EEE-LLL-MM-YYYY`, for example `BDS-014-003-12-2026`.

- `TTT` is a stable three-letter product-type code;
- `EEE` is a stable essence reference: `000` is reserved for no essence, while actual references use `001` through `999`, are never recycled, and do not change with the essence display name;
- `LLL` is `001` through `999`, allocated for the relevant `TTT-EEE` prefix within a month/year, reset when month/year changes, and advanced for every execution;
- `MM` and `YYYY` represent the production month and year.

The internal lot code is a human operational identifier, not a database key or genealogy relation. Automatic generation is optional and backend-authoritative; manual entry remains allowed and does not need to encode source batches. A preview displayed by the frontend neither reserves a sequence nor guarantees the definitive code.

## Balance and retention principles

V1 keeps the established proposal of a materialized `current_quantity` on `inventory_batch` plus immutable `stock_movement` history, updated in the same transaction. The final implementation and its concurrency controls must remain aligned with the relevant ADR.

Items and suppliers with history should be deactivated rather than deleted. Operational batches, production relationships, consumptions, and movements must not be removed merely to clean up data because doing so breaks auditability and genealogy.

Automatic unit conversion is not part of V1. A batch uses its item's unit; all formula and consumption quantities must be compatible with that unit.

## Physical design left open

Production implementation work must decide exact table and column names, keys, indexes, foreign-key layout, constraints, formula versioning needs, and generated-sequence strategy. Those decisions must preserve the conceptual cardinalities and invariants above without changing current module boundaries implicitly.
