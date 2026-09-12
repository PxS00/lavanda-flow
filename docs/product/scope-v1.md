# V1 Product Scope

## Objective

Lavanda Flow V1 must give Céu de Lavanda a simple, reliable, and traceable way to control inventory and register internal production.

V1 covers the whole operational inventory rather than only perfumes or essences. The same inventory model controls raw materials (`matéria-prima`), intermediate products (`produto intermediário`), finalized products (`produto finalizado`), packaging, and other items used by the operation.

## Problems V1 solves

- lack of visibility into available stock;
- duplicate purchases caused by missing inventory information;
- expiration losses;
- missing history for stock entries and withdrawals;
- difficulty identifying batch and supplier origins;
- missing records of the concrete batches used in production;
- inability to trace a finalized product upstream to supplier batches or a supplier batch forward to affected production.

## Included capabilities

### Inventory catalog

An operator can register inventory items with at least a name, category, unit of measure, active status, optional minimum stock, and optional notes.

Initial categories include essence, chemical input, base, alcohol, colorant, fixative, bottle, valve, cap, label, packaging, and other. Categories organize inventory but do not create separate stock systems for raw materials, intermediate products, and finalized products.

### Suppliers and externally supplied batches

Suppliers can be registered and associated with externally supplied inventory batches. Each such batch preserves the lot code assigned by its manufacturer or supplier; Lavanda Flow must not automatically replace that code with an internal code.

### Inventory batches

An inventory item may have multiple independent batches. A batch represents physical stock and has one of two origins:

1. an external supplier or manufacturer; or
2. an internal Céu de Lavanda production execution.

A batch records its item, origin, lot code, quantities, unit of measure, entry or production date, expiration when applicable, and optional notes. Externally supplied and internally produced batches share this inventory and movement model.

### Stock movements, balances, and expiration

Every quantity change creates an auditable stock movement. Supported operations include entry, consumption or withdrawal, positive and negative adjustment, loss, and expired disposal. Corrections create new adjustment movements rather than rewriting history.

The system provides balances by item and batch, zero and low-stock information, expiration status, and movement history. When applicable, eligible batches are prioritized by FEFO (*First Expired, First Out*). Available stock, expiration, FEFO, and consumption eligibility are backend-authoritative.

### Formulas and recipes

V1 includes the minimum formula or recipe information needed for production. A formula defines the inventory items and proportions required for a product. It does not identify the concrete batches consumed by a particular execution; those allocations belong to the production record.

### Internal production

An operator can register a production execution with its formula, output item and quantity, and the exact source batches and quantities actually consumed.

One production execution creates exactly one new output batch. A later execution creates a distinct output batch even when it uses the same product, formula, source batches, and calendar month. A production may consume externally supplied batches, internally produced batches, or multiple batches of the same inventory item.

Produced intermediate batches remain normal inventory batches and may be consumed by later production executions. This supports chains of arbitrary depth, for example:

```text
supplier raw-material batches
            ↓
Base Body Splash production
            ↓
internal Base Body Splash batch
            ↓
Body Splash production
            ↓
internal finalized Body Splash batch
```

Production is one atomic business operation: all source-batch consumption, corresponding auditable movements, and output-batch creation either succeed together or leave no partial production state. Quantities use exact decimal representation, and stock must never become negative.

### Recursive batch genealogy

Genealogy is persisted through explicit production-to-source-batch relationships, not notes or lot-code strings. Operators can navigate it recursively in both directions across any production depth:

- from a finalized or intermediate batch to all upstream source batches, including through other produced batches;
- from a supplier or produced source batch to all downstream internally produced descendants.

Example, upstream:

```text
BDS-014-003-12-2026
│
├── ESS-44821
└── BAS-000-001-12-2026
    │
    ├── ALC-9001
    ├── PG-4432
    └── FIX-2004
```

Example, downstream:

```text
ALC-9001
   ↓
BAS-000-001-12-2026
   ↓
├── BDS-014-003-12-2026
├── BDS-014-004-12-2026
└── ...
```

### Production lot codes

Lot codes are human operational identifiers. They are neither database identity nor the source of genealogy. Exact genealogy always comes from persisted production executions and concrete source-batch consumptions.

#### Bulk and normal internal production

Normal internally produced bulk, intermediate, and other non-packaged outputs keep Céu de Lavanda's existing generated lot format:

```text
TTT-EEE-LLL-MM-YYYY
```

For example, `BDS-014-003-12-2026` means:

- `TTT`: stable three-letter internal product-type code, such as `BDS` for Body Splash, `SBN` for Sabonete, or `BAS` for Base;
- `EEE`: stable three-digit essence reference; `000` is reserved for products with no associated essence, while actual references use `001` through `999`, are never recycled, and remain unchanged if the essence display name changes;
- `LLL`: production sequence from `001` through `999` for the relevant `TTT-EEE` prefix within a calendar month and year; it resets when the month or year changes, and every new production execution receives a new sequence;
- `MM`: production month;
- `YYYY`: production year.

Automatic generation remains recommended but optional for this existing path. The production registration interface offers generated or manual lot-code entry. For automatic generation, the backend assigns the definitive code when production is successfully registered and prevents concurrent allocations from receiving the same code. The frontend must neither reserve nor authoritatively calculate the next sequence. Explicit manual entry remains allowed and is not required to encode genealogy.

#### Packaged finished-product production

Generated packaged finished-product outputs use the shorter package-facing format:

```text
SSS-MM-YYYY
```

For example:

```text
017-09-2026
```

Where:

- `SSS` is exactly three digits from `001` through `999`;
- `SSS` is allocated by the backend globally across generated packaged finished-product outputs within one calendar month/year;
- the packaged sequence does not restart by fragrance, `productionTypeCode`, presentation, or source bulk batch;
- `MM` and `YYYY` are the packaged production/filling month and year;
- the sequence resets for the next calendar month/year.

The generated packaged lot intentionally does not encode `productionTypeCode`, `essenceReference`, source bulk lot codes, presentation size, or packaging lot codes. Those remain structured domain data.

A packaged production execution still creates exactly one output batch. Its explicit genealogy may reference one or multiple bulk finished-product batches and any inventory-controlled bottle, valve, cap, label, or other component batches actually consumed. The packaged lot remains one code regardless of how many valid source batches are recorded.

Packaged generated allocation is backend-authoritative. Angular must not reserve or definitively calculate the next `SSS`. Issue #232 owns implementation of this convention inside the existing `production` architecture; no dedicated filling module is introduced.

This convention does not define packaged-product expiration derivation. Filling must not be assumed to renew, copy, shorten, or otherwise derive expiration unless a separately approved shelf-life rule establishes that behavior.

### Search and operational dashboard

V1 supports item search and quick access to total available quantity, batches, expiration, supplier or internal-production origin, and movement history. The dashboard remains limited to useful operational information such as active items, low or zero stock, and expiring or expired batches.

## Initial import

The existing inventory CSV is the source for the initial migration. Names, references, quantities, expiration dates, and missing or inconsistent values must be normalized before import. The CSV is not a source of truth after migration.

## Outside V1

V1 does not include:

- production costing or margins;
- sales, invoicing, or other fiscal features;
- purchase orders, supplier integration, or automatic purchasing forecasts;
- barcodes or QR codes unless separately approved;
- automatic unit conversion;
- advanced formula lifecycle or versioning beyond the minimum recipe definitions required for production;
- automatic production planning or scheduling, unattended execution of the production process, and broader manufacturing automation; operator-triggered production registration remains part of V1.

These unrelated ERP capabilities must not expand V1.

## Success criteria

V1 is operationally useful when an operator can, without relying on the spreadsheet:

1. search an item and see its quantity, batches, expiration, and origin;
2. register a purchase or stock entry and preserve its supplier lot code;
3. register consumption, adjustments, losses, and disposal with auditable history;
4. register an internal production and see its generated or manually entered output lot;
5. identify the exact source batches and quantities consumed by a production;
6. trace a finalized product recursively through intermediate batches to supplier batches;
7. trace a supplier batch forward to all affected internally produced batches;
8. quickly identify low, zero, expiring, and expired stock.

## Product principles

- simplicity of use over feature count;
- mobile-first interface;
- historical records instead of destructive changes;
- traceability represented explicitly in the data model;
- stock consistency over technical convenience;
- incremental evolution without turning V1 into a complete ERP.
