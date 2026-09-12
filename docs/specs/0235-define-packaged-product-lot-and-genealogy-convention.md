# Issue #235 — Packaged-product lot and genealogy convention

## Status

Approved implementation specification for issue #235.

## Source of truth

Issue #235 is the source of truth. This specification makes its domain decision versioned and explicit across the authoritative V1 documentation.

Read together with:

- `AGENTS.md`;
- `backend/AGENTS.md`;
- `docs/product/scope-v1.md`;
- `docs/domain/domain-model.md`;
- `docs/architecture/architecture.md`;
- `docs/architecture/data-model.md`;
- issue #140 production/genealogy decisions;
- issue #229 finished-product/reference semantics;
- issue #231 corrected bulk-perfume import semantics.

## Objective

Define one stable lot-code and genealogy contract for packaged finished-product outputs before issue #232 implements the filling/bottling workflow.

The documentation must clearly distinguish:

1. normal internally produced bulk/intermediate outputs, which keep the existing generated lot format; and
2. packaged finished-product outputs, which use a shorter generated lot format suitable for final-package printing.

The packaged lot string remains an operational identifier only. Exact genealogy continues to come from persisted production/source-batch relationships.

## Existing invariant preserved

Normal internal production continues to use:

```text
TTT-EEE-LLL-MM-YYYY
```

Example:

```text
PFM-014-003-09-2026
```

Semantics remain unchanged:

- `TTT`: stable three-letter production type code;
- `EEE`: stable essence reference, with `000` reserved for no essence and `001`–`999` for actual fragrance references;
- `LLL`: three-digit sequence allocated for the relevant `TTT-EEE` prefix inside one calendar month/year;
- `MM-YYYY`: production month/year.

The existing backend-authoritative sequence allocation, collision prevention, optional manual lot entry, transaction semantics, and genealogy rules remain unchanged.

## Packaged finished-product generated lot

A generated packaged finished-product output uses:

```text
SSS-MM-YYYY
```

Example:

```text
017-09-2026
```

### `SSS`

- exactly three digits;
- valid range `001` through `999`;
- allocated by the backend;
- global across generated packaged finished-product outputs within the same calendar month/year;
- does not restart per fragrance;
- does not restart per `productionTypeCode`;
- does not restart per packaged presentation;
- does not restart per source bulk batch;
- resets when the calendar month/year changes.

Within one month/year the generated packaged sequence therefore progresses conceptually as:

```text
001-09-2026
002-09-2026
003-09-2026
...
999-09-2026
```

Issue #232 owns the implementation details for allocating this sequence safely and atomically.

## Backend authority

Angular may display a backend-confirmed lot after successful registration, but it must not:

- reserve the next packaged sequence;
- calculate the authoritative next `SSS`;
- assume a locally predicted lot will remain available;
- derive the final packaged lot from source lots or catalog metadata.

Concurrent allocation and collision prevention belong to the backend/PostgreSQL transaction boundary implemented by #232.

## Lot code versus identity

The packaged lot code is:

- a human operational identifier;
- printable on a final package;
- not database identity;
- not product identity;
- not formula identity;
- not genealogy authority.

The generated packaged lot intentionally does not encode:

- `productionTypeCode`;
- `essenceReference`;
- source bulk lot codes;
- presentation size;
- consumed packaging lot codes.

Those concepts remain represented by their own structured domain data.

## Genealogy

A packaged output remains the output batch of exactly one production execution under the existing production invariant.

That production execution persists the concrete source batches and exact consumed quantities. Genealogy must therefore be navigable through explicit production relationships rather than lot-code parsing.

A packaged execution may consume, when allowed by the approved formula/execution contract:

- one bulk finished-product batch;
- multiple bulk finished-product batches;
- bottle batches;
- valve batches;
- cap batches;
- label batches;
- other inventory-controlled packaging/components;
- any other valid source batch required by the production formula.

Example with one bulk source:

```text
PFM-014-003-09-2026  bulk source
             |
             v
017-09-2026           packaged output
```

Example with more than one bulk source:

```text
017-09-2026
├── PFM-014-003-09-2026
└── PFM-014-004-09-2026
```

The generated packaged lot remains one code even when genealogy contains multiple source batches.

## Inventory and production ownership

Existing module ownership remains unchanged:

- `catalog` owns stable item metadata;
- `inventory` owns batches, balances, movements, FEFO, expiration, and stock invariants;
- `production` owns production orchestration, source-batch consumptions, genealogy, and lot-code allocation;
- no dedicated filling module is introduced.

Issue #232 must implement filling/bottling inside the existing production architecture and cross other modules only through public APIs.

## Expiration policy

This issue does not define packaged-product expiration derivation.

A packaged output's expiration remains explicit under the existing production/output contract unless a later approved shelf-life rule changes that behavior.

Do not infer from this issue that filling:

- renews expiration;
- copies the source bulk expiration;
- shortens expiration;
- derives expiration from the lot string.

Genealogy to source batches must be preserved independently of expiration policy.

## Documentation changes

Update the following authoritative documents:

### `docs/product/scope-v1.md`

- distinguish normal internal generated lots from packaged generated lots;
- add `SSS-MM-YYYY` and its sequence semantics;
- keep backend authority explicit;
- keep genealogy independent from lot strings;
- identify #232 as the filling implementation owner.

### `docs/domain/domain-model.md`

- define packaged output lot semantics next to the existing internal production lot policy;
- preserve one execution -> one output batch;
- preserve explicit one-or-many source-batch genealogy;
- state that packaged lot generation does not change module/domain ownership;
- keep expiration derivation explicitly undecided.

### `docs/architecture/architecture.md`

- document that production owns both normal internal and packaged generated lot allocation;
- distinguish the two generated formats without creating another module;
- reinforce that the frontend cannot reserve either sequence;
- keep PostgreSQL/backend authority for definitive allocation.

### `docs/architecture/data-model.md`

- distinguish normal internal lot-sequence semantics from packaged sequence semantics;
- document packaged sequence scope as global by month/year across packaged outputs;
- keep genealogy on production/consumption/output relations;
- do not require a physical schema change in this documentation issue.

## Acceptance mapping

The issue is complete when the authoritative documentation establishes all of the following consistently:

- normal internal generated lot: `TTT-EEE-LLL-MM-YYYY`;
- packaged generated lot: `SSS-MM-YYYY`;
- `SSS` exactly `001`–`999`;
- packaged sequence global per month/year and reset for the next month/year;
- packaged generated allocation backend-authoritative;
- no `productionTypeCode` or `essenceReference` encoded into packaged generated lots;
- lot code explicitly non-identity and non-genealogy authority;
- explicit genealogy supports one or multiple bulk source batches plus packaging/component inputs;
- one packaged production execution still creates exactly one output batch;
- #232 owns implementation in v0.6.1;
- normal internal lot behavior unchanged;
- no expiration derivation introduced;
- no code, API, schema, dependency, frontend, or CI change.

## Constraints

- Preserve the modular monolith and current module boundaries.
- Preserve existing production transaction and audit invariants.
- Preserve existing normal generated lot behavior.
- Do not infer packaged-product identity from display names.
- Do not infer genealogy from lot-code strings.
- Do not introduce a dedicated filling module.
- Do not add sales, POS, customer, order, barcode, QR-code, pricing, fiscal, or label-printing scope.

## Out of scope

- Filling/bottling production code;
- packaged sequence persistence/allocation implementation;
- catalog creation workflow for packaged presentations beyond documentation needed by #232;
- expiration or shelf-life derivation;
- label/artwork generation or printing;
- barcode/QR support;
- sales/order/customer records;
- Flyway migrations;
- HTTP/API changes;
- frontend changes.

## Validation

Documentation-only validation:

```text
git diff --check
git diff
git status --short
```

A PR for this issue must contain documentation changes only and target `develop`.
