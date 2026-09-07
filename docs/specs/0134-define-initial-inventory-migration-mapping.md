# Issue #134 — Define initial inventory migration mapping

## Objective

Define the authoritative, deterministic mapping from the current Céu de Lavanda inventory CSV into the existing Lavanda Flow V1 catalog, batch, and stock-history model.

This issue changes documentation only. It does not perform the import and does not change production code, schema, API contracts, or dependencies.

## Source of truth

Apply, in order:

1. GitHub issue #134;
2. `AGENTS.md`;
3. `docs/product/scope-v1.md`;
4. the current catalog/inventory domain and PostgreSQL schema;
5. this specification for the concrete normalization decisions required by #134.

The current CSV is a one-time migration source. PostgreSQL becomes the source of truth after a successful import.

## Source shape

The current source has 82 data rows and these columns:

- `Nome do Perfume`;
- `Genero`;
- `Ml Disponiveis`;
- `Expired`.

Known source anomalies from #134 must be handled explicitly:

- 81 distinct raw names across 82 rows;
- one repeated raw name: `Scandall`, representing distinct source references;
- leading/trailing whitespace in names;
- `Genero` values `F`, `M`, `M/C`, `C`, `F / C`, and ` F`;
- one zero-stock row;
- month/year expiration values;
- embedded whitespace in `09/2 7`;
- blank expiration for the zero-stock `Hugo Boss Men` row;
- no supplier, lot-code, or historical receipt-date fields.

## Column mapping

| Source column | Normalization | V1 destination |
| --- | --- | --- |
| `Nome do Perfume` | trim leading/trailing whitespace; preserve spelling, case, accents, and intentional internal characters | `InventoryItem.name`, with duplicate disambiguation defined below |
| `Genero` | trim, uppercase, remove whitespace around `/`; canonical values are treated as opaque legacy reference tokens | validation/disambiguation only; no new V1 field or enum |
| `Ml Disponiveis` | trim and parse directly as exact decimal; never through `double`/`float` | opening quantity in `BigDecimal`/`NUMERIC(19,6)` semantics |
| `Expired` | remove whitespace, parse source month/year, convert to one exact cutoff date | `InventoryBatch.expiresAt` for positive opening stock |

No source column may silently become an invented supplier, lot code, purchase date, brand, formula, gender taxonomy, or other domain concept.

## Name normalization

For `Nome do Perfume`:

1. trim leading and trailing whitespace;
2. reject the row if the result is blank;
3. preserve internal spelling, punctuation, accents, capitalization, and spacing unless the source issue identifies a specific normalization defect;
4. do not auto-correct perfume names or merge names using fuzzy matching.

For duplicate detection during the one-time import, compare the trimmed names case-insensitively using locale-independent rules. This duplicate key is migration-only and is not a new domain identity.

## Legacy `Genero` / reference normalization

`Genero` is not promoted to a V1 domain field.

Normalize only for validation and duplicate disambiguation:

1. trim leading/trailing whitespace;
2. uppercase with locale-independent rules;
3. remove whitespace surrounding `/`;
4. accept the canonical values observed in the current source: `F`, `M`, `C`, `M/C`, and `F/C`.

Examples:

- ` F` -> `F`;
- `F / C` -> `F/C`.

For rows whose normalized name is unique, the canonical reference is not persisted.

For rows whose normalized name collides with another row but whose canonical reference differs, preserve both records by disambiguating the catalog display name as:

```text
<normalized name> (<canonical reference>)
```

Therefore the two `Scandall` source references must become two distinct catalog items rather than being silently merged.

If two rows have the same normalized name and the same canonical reference, or a duplicate cannot be deterministically disambiguated, validation must reject the conflicting rows instead of merging them.

## Catalog mapping

Every valid source row creates one active inventory catalog item.

For this legacy perfume/essence stock source:

- category: `ESSENCE`;
- unit of measure: `MILLILITER`;
- active: `true`;
- description/notes: no migration-only metadata is invented.

The importer must use the existing catalog model rather than introducing a perfume-specific aggregate or gender/reference enum.

## Quantity mapping

`Ml Disponiveis` is parsed using `BigDecimal` semantics.

Rules:

- trim the textual value before parsing;
- reject blank, malformed, negative, non-finite, or exponent-style values not present in the approved source format;
- allow zero;
- allow at most 6 fractional decimal places, matching `NUMERIC(19,6)`;
- reject excess precision instead of silently rounding;
- never parse through `double` or `float`.

A positive quantity represents opening physical stock.

A zero quantity represents a valid catalog item with no opening stock.

## Expiration mapping

The source month/year is normalized into the last calendar day of that month.

For the observed two-digit-year source format:

```text
MM/YY -> YearMonth.of(2000 + YY, MM).atEndOfMonth()
```

Whitespace is removed before parsing, so:

```text
09/2 7 -> 09/27 -> 2027-09-30
```

This exact date is passed unchanged to the existing backend semantics, where `expiresAt <= today` is expired.

Rules:

- reject invalid months or malformed nonblank expiration values;
- a blank expiration is allowed when quantity is zero because no opening batch is created;
- a positive-stock row with blank expiration must be rejected rather than creating untracked expiring stock from this source.
- a positive-stock row whose normalized expiration is before the migration effective date must be rejected, matching the existing batch invariant that `expiresAt` cannot precede `receivedAt`.

Do not infer expiration from another row or invent a default shelf life.

## Missing supplier, lot code, and receipt history

The CSV contains no trustworthy historical supplier, supplier lot code, or receipt date.

Therefore:

- `supplierId`: `null`;
- `lotCode`: `null`;
- no placeholder supplier is created;
- no synthetic supplier/manufacturer lot code is generated.

The existing batch schema requires `receivedAt`. For opening-stock batches, the later importer must use an explicit migration effective date from its execution context as the batch entry date.

That date represents **when Lavanda Flow established the opening snapshot**, not a claim about when the stock was originally purchased or received. Documentation/reporting for the import must make this distinction explicit.

The importer must validate the effective date against every positive-stock expiration before writing. It must not backdate the effective date to make an already-expired source row pass validation.

The opening movement timestamp likewise records the migration/apply operation, not reconstructed purchase history.

## Row outcomes

### Positive quantity

A valid row with quantity greater than zero creates:

1. one inventory catalog item;
2. one opening inventory batch with:
   - `supplierId = null`;
   - `lotCode = null`;
   - `initialQuantity = source quantity`;
   - `currentQuantity = source quantity`;
   - `receivedAt = migration effective date`;
   - `expiresAt = normalized exact cutoff date`;
3. one auditable opening `ENTRY` movement for the same exact quantity.

The movement reason should identify the operation as the initial inventory migration/snapshot without pretending it was a historical purchase receipt.

### Zero quantity

A valid zero-stock row creates:

1. one inventory catalog item;
2. no inventory batch;
3. no stock movement.

This applies to the observed `Hugo Boss Men` row. Its blank expiration therefore creates no fabricated expiration or history.

### Invalid row

A row is rejected during validation when any required mapping is ambiguous or invalid, including:

- blank normalized name;
- unsupported/blank legacy reference when needed for deterministic duplicate disambiguation;
- malformed or negative quantity;
- quantity precision beyond 6 decimals;
- malformed nonblank expiration;
- positive quantity with blank expiration;
- normalized expiration before the migration effective date;
- unresolved duplicate identity.

No rejected row may be silently skipped in apply mode.

## Deterministic validation report

The later importer must validate the full input before writes and report row-level outcomes deterministically.

Each reported row should identify at least:

- source row number;
- normalized catalog name;
- normalized legacy reference when relevant;
- normalized quantity;
- normalized expiration or `null`;
- outcome: catalog-only, opening-stock, or rejected;
- validation code/reason for rejected rows.

Dry-run and apply must use the same normalization rules.

## Expected current-source interpretation

Given the source characteristics recorded in #134, assuming no additional invalid values are discovered during importer validation and an effective date no later than the earliest positive-stock expiration:

- all 82 source rows remain represented as catalog records;
- the repeated `Scandall` records remain distinct through deterministic reference-based name disambiguation;
- the single zero-stock row creates no batch or movement;
- the remaining positive-stock rows create opening batches and `ENTRY` movements;
- `09/2 7` normalizes successfully;
- no supplier, lot code, or historical receipt fact is invented.

The importer must still validate the real file and must not hard-code these counts as a substitute for parsing.

## Acceptance criteria

- [ ] Every CSV column has an explicit mapping or discard/disambiguation rule.
- [ ] Name and legacy-reference whitespace normalization is deterministic.
- [ ] `Genero` remains a migration-only opaque reference and introduces no new domain enum/field.
- [ ] Duplicate `Scandall` rows remain distinct and cannot be silently merged.
- [ ] Quantities use exact `BigDecimal`/`NUMERIC(19,6)` rules with no silent rounding.
- [ ] Zero stock creates no fabricated batch or movement.
- [ ] Month/year expiration uses one exact documented cutoff compatible with `expiresAt <= today`.
- [ ] `09/2 7` is deterministically normalized.
- [ ] Blank expiration behavior is explicit.
- [ ] Missing supplier, lot code, and historical receipt date do not create false history.
- [ ] Positive rows, zero rows, and rejected rows have explicit domain outcomes.
- [ ] CSV data is migration input only; PostgreSQL is authoritative afterward.
- [ ] No production code, schema migration, API contract, dependency, or runtime CSV feature is added by #134.

## Out of scope

- Executing the import;
- implementing dry-run/apply tooling;
- committing the operational CSV into the repository;
- adding a recurring CSV import endpoint or UI;
- adding supplier data absent from the source;
- adding perfume brands, gender taxonomy, formulas, costing, sales, or commerce metadata;
- minimum-shelf-life policy from #73;
- authentication or deployment.

## Follow-up

Issue #136 must consume these mapping rules as its source normalization contract and must not introduce conflicting normalization behavior.
