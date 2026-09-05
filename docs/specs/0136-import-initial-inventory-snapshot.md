# Issue #136 — Import initial inventory snapshot

## Objective

Implement the one-time, offline initial inventory migration defined by GitHub issue #136, consuming the normalization contract from #134 and preserving catalog, batch, balance, audit-history, transaction, and module-boundary invariants.

The real Céu de Lavanda CSV is migration input only. It must remain outside the repository, and PostgreSQL becomes the operational source of truth after a successful apply.

## Source of truth

Apply, in order:

1. GitHub issue #136;
2. `AGENTS.md`;
3. `backend/AGENTS.md`;
4. `docs/specs/0134-define-initial-inventory-migration-mapping.md`;
5. `docs/product/scope-v1.md`;
6. current catalog and inventory public contracts, domain invariants, persistence schema, and stock-receipt behavior;
7. this specification for the implementation-specific delta of #136.

Do not change #134 normalization semantics inside the importer.

## Architectural ownership

The import use case belongs to the `inventory` module because it creates opening inventory state and audit history.

`inventory` must not import `catalog.application`, catalog repositories, catalog domain entities, or catalog infrastructure.

The current catalog public API is read-oriented. Add only the smallest published catalog write contract required to create one inventory item from another module:

- place the contract and immutable values in the public `catalog` package;
- return stable item identity/immutable values only;
- do not expose `catalog.domain.Category`, JPA types, repositories, or application internals;
- adapt the public contract to the existing catalog registration/domain path rather than creating a second catalog creation policy.

If the re-import guard needs to know whether any catalog item already exists, extend an existing public catalog lookup with the smallest `existsAny`-style operation rather than exposing all catalog persistence state.

No new application module, migration module, event flow, message bus, or shared business service is introduced.

## Offline execution boundary

This is an administrative one-time execution path, not a runtime product API.

Use a small Spring Boot runner/adapter under inventory infrastructure that is disabled by default and runs only when explicitly enabled through typed configuration.

Preferred configuration surface:

```text
lavanda.inventory.initial-import.enabled
lavanda.inventory.initial-import.mode
lavanda.inventory.initial-import.file
lavanda.inventory.initial-import.effective-date
```

Modes:

```text
DRY_RUN
APPLY
```

Requirements:

- `enabled` defaults to `false`;
- when disabled, normal application startup and runtime behavior are unchanged;
- when enabled, `mode`, external file path, and explicit effective date are required;
- the CSV path is external filesystem input, never a classpath/repository resource;
- the effective date is explicit in both modes and is not silently defaulted from `Clock`;
- no HTTP endpoint is added;
- no browser upload or recurring synchronization path is added.

Operational invocation may run the Spring application as a non-web process using `--spring.main.web-application-type=none`. Do not change normal application web configuration globally for this importer.

Add concise operational documentation showing dry-run first and apply second, with the external file path and effective date supplied explicitly.

## Source parsing

The importer is source-specific for the approved Céu de Lavanda snapshot. It is not a generic CSV framework.

Read UTF-8 input and require the four #134 columns:

```text
Nome do Perfume
Genero
Ml Disponiveis
Expired
```

The implementation must inspect the actual approved source shape and support only the CSV quoting/delimiter behavior it genuinely requires. Do not add a CSV dependency or build a speculative general-purpose parser.

Parsing rules:

- preserve source row number, with the header excluded from data-row numbering context;
- reject malformed row structure rather than shifting columns silently;
- normalize every field exactly as #134 specifies;
- process duplicate-name disambiguation as a full-file concern before any apply write;
- preserve deterministic source order in reports;
- never hard-code the expected 82 rows as a substitute for parsing the file.

## Normalization and validation

#134 is authoritative.

At minimum preserve:

- outer trim for names;
- locale-independent normalization for legacy `Genero` tokens;
- `F / C -> F/C` and ` F -> F`;
- duplicate comparison on normalized names using locale-independent case-insensitive semantics;
- the two `Scandall` references remaining distinct as `Scandall (M)` and `Scandall (F)`;
- category `ESSENCE`;
- unit `MILLILITER`;
- no invented description, supplier, lot code, receipt history, essence reference, or production type code;
- exact `BigDecimal` parsing with at most 6 fractional digits and no silent rounding;
- zero quantity allowed;
- month/year expiration mapped to the last day of the month;
- embedded expiration whitespace normalized, including `09/2 7 -> 2027-09-30`;
- positive quantity requires expiration;
- zero quantity may have blank expiration;
- positive rows whose expiration is before the supplied effective date are rejected;
- the effective date must never be backdated automatically to bypass the batch invariant.

The importer must validate the complete input before the first apply write.

Validation errors are accumulated into deterministic row-level results when possible. One invalid row makes `APPLY` fail before creating any catalog item, batch, or movement.

Use stable validation codes/reasons rather than relying only on exception text. Keep the validation taxonomy small and specific to this one-time importer.

## Dry-run

`DRY_RUN` executes the same parsing, normalization, duplicate resolution, and validation path used by `APPLY`.

It must perform zero database writes:

- no catalog item inserts;
- no batches;
- no stock movements;
- no migration marker/state;
- no sequence reservation or synthetic data.

Database reads are allowed only when needed for validation/reporting; dry-run must not mutate state indirectly.

A successful dry-run reports what would happen without persisting generated UUIDs as part of the deterministic business result.

Dry-run does not weaken or bypass the apply-time initialization guard.

## Deterministic report

Return/emit one report containing at least:

- mode;
- effective date;
- total data-row count;
- catalog-only count;
- opening-stock count;
- rejected count;
- ordered row results.

Each row result contains at least:

- source row number;
- normalized catalog name when available;
- normalized legacy reference when relevant;
- normalized quantity when valid;
- normalized expiration or `null`;
- outcome: `CATALOG_ONLY`, `OPENING_STOCK`, or `REJECTED`;
- validation code/reason for rejected rows.

For valid input, `DRY_RUN` and `APPLY` must produce the same normalization/outcome classification. Apply-only generated identifiers are not required in the deterministic report.

The runner should print/log the report clearly before returning or failing so the operator can inspect the result.

## Apply-time initialization guard

`APPLY` is allowed only against an uninitialized operational catalog/inventory state.

Use the simplest reliable guard compatible with the current architecture:

- before the first write, reject apply when any catalog inventory item already exists, active or inactive;
- obtain this fact through a published catalog contract, never a catalog repository import;
- do not rely on matching source names or on detecting previously generated UUIDs;
- do not persist a new dashboard/import-state table solely as a guard.

Because inventory batches reference catalog items, an empty catalog is the authoritative prerequisite for this initial snapshot operation. Supplier records alone do not make the inventory initialized.

The guard applies to `APPLY`; a read-only dry-run may still validate a file independently.

## Atomic apply

The complete apply is one transaction owned by the inventory import application use case.

Required ordering:

```text
parse full file
-> normalize full file
-> validate full file
-> verify initialization guard
-> create catalog items
-> create opening stock for positive rows
-> commit once
```

Do not commit per row.

Do not use `REQUIRES_NEW` in the catalog registration or receipt path.

### Catalog items

Every valid row creates one active catalog item through the new public catalog registration contract.

Imported rows use:

- category `ESSENCE`;
- unit `MILLILITER`;
- no description unless #134 explicitly provides one (the current mapping does not);
- no essence reference;
- no production type code.

The catalog registration adapter must join the outer transaction.

### Positive opening stock

For every row with quantity greater than zero, reuse the existing inventory stock-receipt path rather than duplicating batch/movement creation rules.

Pass:

- the newly created stable inventory item ID;
- `supplierId = null`;
- `lotCode = null`;
- exact normalized quantity;
- `receivedAt = effective date`;
- normalized exact expiration;
- a concise pt-BR audit reason identifying the initial inventory import, for example `Importação inicial do estoque`.

`RegisterStockReceipt` remains authoritative for creating the batch and exactly one immutable initial `ENTRY` movement.

Its transaction must join the outer import transaction so a later failure rolls back earlier catalog items, batches, and movements.

### Zero opening stock

For quantity equal to zero:

- create the catalog item;
- create no batch;
- create no movement.

Do not call the receipt use case with zero quantity solely to manufacture history.

## Failure behavior

- malformed/invalid input: report all deterministically discoverable validation failures and perform no writes;
- initialized catalog: reject apply before writes;
- catalog/domain/persistence failure during apply: roll back the entire snapshot;
- no failed apply may leave a subset of catalog items, batches, or movements committed.

Do not catch an apply exception and then commit partial state.

## Persistence and schema

No operational inventory data is seeded through Flyway.

No new schema is required solely for this import unless a concrete existing invariant proves otherwise during implementation. The expected solution adds no migration table, no import marker table, and no data-seeding migration.

PostgreSQL remains the source of truth after successful apply. Normal runtime code must never read the CSV.

## Tests

Use synthetic test CSV data. Do not commit the real Céu de Lavanda operational CSV as a test fixture.

### Parsing/normalization tests

Cover at minimum:

- expected four-column source shape;
- name whitespace trimming;
- `Genero` normalization variants;
- duplicate `Scandall` disambiguation;
- exact decimal parsing and excess precision rejection;
- zero quantity;
- month/year end-of-month cutoff;
- `09/2 7` normalization;
- blank expiration for zero stock;
- rejection of blank expiration for positive stock;
- rejection when expiration precedes effective date;
- malformed row/column count;
- deterministic row ordering and validation codes.

### PostgreSQL/Testcontainers integration

Cover at minimum:

1. successful dry-run with zero database writes;
2. successful apply creating the expected catalog items;
3. positive rows creating one batch and one `ENTRY` movement each;
4. zero-stock row creating no batch/movement;
5. persisted current balance matching imported quantity;
6. batch query/current-stock behavior agreeing with persisted import state;
7. invalid input producing zero writes;
8. duplicate-execution protection after initialization;
9. a simulated mid-apply failure rolling back earlier catalog/inventory writes without weakening production code;
10. transaction participation across the public catalog registration and existing stock-receipt path.

Use a test-only failing collaborator/adapter where necessary to prove rollback; do not add failure hooks to production code.

Use deterministic effective dates in tests. Do not use future dates that will become invalid with time.

Existing Spring Modulith verification must remain green.

## Operational documentation

Document the one-time workflow with commands equivalent to:

1. run `DRY_RUN` against the external CSV and explicit effective date;
2. inspect a zero-rejection report;
3. run `APPLY` against the same source/effective date;
4. verify resulting catalog/current-stock/batch/history state;
5. stop using the CSV operationally after apply.

Do not document or expose a recurring import process.

## Acceptance criteria

- [ ] #134 normalization is consumed without conflicting rules.
- [ ] The real source can be supplied externally without being committed to the repository.
- [ ] Explicit `DRY_RUN` performs complete parsing/normalization/validation with zero writes.
- [ ] Deterministic row-level report identifies source context and outcomes.
- [ ] Every invalid apply fails before the first business write when the invalidity is discoverable during full-file validation.
- [ ] Exact quantities use `BigDecimal`/`NUMERIC(19,6)` semantics with no silent rounding.
- [ ] All valid rows create catalog items through a published catalog contract.
- [ ] Positive rows reuse existing stock-receipt behavior and create one batch plus one immutable `ENTRY` movement.
- [ ] Zero-stock rows create no batch or movement.
- [ ] Expiration/effective-date behavior matches #134 and existing batch invariants.
- [ ] No supplier, lot code, receipt date, production metadata, or other historical fact is invented.
- [ ] Duplicate `Scandall` rows remain distinct.
- [ ] `APPLY` is one transaction across catalog and inventory effects.
- [ ] Mid-apply failures roll back the complete snapshot.
- [ ] Apply refuses an already initialized catalog before writes.
- [ ] No Flyway operational-data seed, import-state table, public HTTP import endpoint, recurring CSV feature, or new dependency is introduced.
- [ ] PostgreSQL/Testcontainers coverage proves dry-run, apply, zero-stock, invalid-input safety, rollback, duplicate guard, and balance/history consistency.
- [ ] Spring Modulith boundaries remain valid.
- [ ] `./mvnw verify` succeeds.

## Out of scope

- generic/recurring CSV import;
- browser upload/import UI;
- spreadsheet synchronization;
- supplier reconstruction;
- perfume gender taxonomy or new perfume aggregate;
- production/formula/cost/sales/purchasing history import;
- minimum-shelf-life policy from #73;
- deployment/provider/authentication changes.

## Final validation

Before finishing implementation:

1. run `./mvnw verify`;
2. review the complete `git diff` for module-boundary and scope violations;
3. run `git diff --check`;
4. run `git status --short`.