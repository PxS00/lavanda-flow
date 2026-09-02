# Domain Model

## Overview

Lavanda Flow models inventory through items, batches, and auditable stock movements. V1 also models the minimum formula and production concepts needed to create internally produced batches and trace their genealogy.

Raw materials (`matéria-prima`), intermediate products (`produto intermediário`), and finalized products (`produto finalizado`) use one coherent inventory and batch model. A batch may come from an external supplier or from internal Céu de Lavanda production.

The concept names below describe domain responsibilities. They do not freeze class, package, API, or persistence names for implementation.

## Core concepts

### Inventory item

Represents anything controlled in inventory, including an essence, chemical input, internally produced base, finalized Body Splash, bottle, valve, or label.

It identifies the item, category, standard unit of measure, active status, and optional minimum stock. One item may have many batches; an item does not represent a purchase or production execution.

### Batch

Represents identifiable physical stock of one inventory item. A batch has exactly one origin:

- **external**: received from a supplier or manufacturer and retaining that source's lot code; or
- **internal**: the output of exactly one production execution and identified operationally by an internal lot code.

Different receipts and production executions create distinct batches. The same product, formula, source batches, and month used in a later execution still produce a new batch.

A batch records the information needed to identify its item, origin, lot code, relevant dates, initial quantity, and available quantity. Supplier lot codes are not automatically replaced by internal codes.

### Stock movement

Represents every auditable stock change, including entry, consumption, positive or negative adjustment, loss, and expired disposal. It identifies the affected batch, type, exact decimal quantity, occurrence time, and reason when required.

Confirmed movements are historical. Corrections create new adjustment movements instead of rewriting or deleting history.

### Supplier

Represents the commercial source of externally supplied stock. It supports basic identification and contact information and allows externally supplied batches to be located by supplier. An internally produced batch has a production origin rather than a supplier origin.

### Category and unit of measure

Categories organize items and may include essence, chemical input, base, alcohol, colorant, fixative, bottle, valve, cap, label, packaging, and other. Classification does not create separate inventory models or determine every business rule.

Quantities use exact decimal representation: `BigDecimal` in the backend and an appropriate `NUMERIC`/`DECIMAL` database type. Automatic conversion between units is not part of V1.

### Formula or recipe

Defines which inventory items and proportions are required to produce an output item. It describes requirements, not the concrete source batches used by an execution.

Only the minimum formula information needed to register production is approved in V1. Detailed lifecycle and versioning decisions remain for implementation specifications.

### Production execution

Represents one completed internal production operation. It references the applicable formula or recipe, records the produced item and quantity, creates exactly one output batch, and contains the actual source-batch consumptions.

Each execution is distinct. Repeating identical inputs later never reuses the earlier output batch.

### Production consumption

Records one concrete source batch and the exact quantity consumed by one production execution. A production may consume:

- externally supplied batches;
- internally produced batches;
- more than one batch of the same inventory item when needed.

The production record therefore captures actual allocation, while the formula captures required items and proportions.

## Relationships and genealogy

```text
External or previously produced source batches + exact quantities
                              │ consumed by
                              ▼
                    Production execution A
                              │ creates exactly one
                              ▼
                  Intermediate output batch
                              │ may later be consumed by
                              ▼
                    Production execution B
                              │ creates exactly one
                              ▼
                    Finalized output batch
```

An internally produced intermediate batch may be a source batch for a later production. Repeating this relationship forms a directed genealogy of arbitrary depth; it is not hard-coded to raw material -> base -> final.

Genealogy is explicit and bidirectional:

- **upstream**: from a produced batch, follow its production execution and consumptions recursively to all supplier or earlier produced source batches;
- **downstream**: from any source batch, follow every consuming production and its output batch recursively to all produced descendants.

Lot codes and notes may help operators recognize stock but are not database identity and must not be used to infer genealogy.

## Invariants

### Quantities and stock

- movement and consumption quantities are greater than zero;
- available stock never becomes negative;
- `double` and `float` are never used for quantities;
- movement and consumption units are compatible with their items and batches;
- FEFO and inventory eligibility are backend-authoritative;
- expired batches are excluded from normal consumption; `expiresAt <= today` means expired;
- date-dependent rules use the application `Clock`.

### Batches and history

- separate receipts and production executions never overwrite earlier batches;
- external batches preserve manufacturer or supplier lot codes;
- every stock change creates an auditable movement;
- corrections create new adjustment movements;
- operational batches and movements are not deleted merely to clean up data.

### Production atomicity

A production execution is one atomic business operation. It must validate and persist all of the following in one successful transaction:

1. exact source-batch allocations and quantities;
2. source-batch balance reductions and consumption history;
3. the distinct output batch and its stock-creating history;
4. the explicit relationships that support genealogy.

If any part fails, no source balance reduction, consumption record, output batch, or partial production state remains. PostgreSQL is the source of truth, and Flyway controls schema evolution.

## Internal production lot policy

Internally produced batches use the operational format `TTT-EEE-LLL-MM-YYYY`, for example `BDS-014-003-12-2026`:

- `TTT` is a stable three-letter product-type code such as `BDS`, `SBN`, or `BAS`;
- `EEE` is a stable essence reference: `000` means no essence; actual references use `001` through `999`, are never recycled, and survive display-name changes;
- `LLL` is a sequence from `001` through `999` for the relevant `TTT-EEE` prefix in a calendar month/year; every production execution receives a new sequence, reset when month/year changes;
- `MM` and `YYYY` are the production month and year.

Automatic generation is recommended but optional. For generated codes, the backend authoritatively assigns the definitive collision-free sequence when the transaction succeeds; a frontend preview does not reserve it. Manual entry remains available for explicit operational cases and need not encode genealogy.

## V1 use cases

- register and classify an inventory item;
- register externally supplied stock while preserving its source lot code;
- register consumption, adjustment, loss, and expired disposal;
- query balances, expiration, and movement history by item or batch;
- define the minimum formula or recipe inputs for production;
- register one production execution with exact source-batch allocations and one new output batch;
- consume a produced intermediate batch in a later production;
- navigate batch genealogy recursively upstream and downstream.

Business rules belong in the domain or application layer. Controllers and frontend components only validate their boundaries, invoke use cases, and present results.
