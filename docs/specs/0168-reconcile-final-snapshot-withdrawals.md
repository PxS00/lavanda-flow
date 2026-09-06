# Issue #168 — Reconcile final snapshot withdrawal adjustments

## Objective

Extend the one-time initial inventory migration so the frozen final Céu de Lavanda spreadsheet snapshot can be imported without modifying the source file, while preserving the original #134/#136 four-column contract and all existing inventory invariants.

This is a source-compatibility correction for the initial migration only. It is not a recurring CSV-import feature and it must not fabricate historical withdrawal events that the spreadsheet cannot support reliably.

## Source of truth

Apply, in order:

1. GitHub issue #168;
2. `AGENTS.md`;
3. `backend/AGENTS.md`;
4. `docs/specs/0134-define-initial-inventory-migration-mapping.md`;
5. `docs/specs/0136-import-initial-inventory-snapshot.md`;
6. current `inventory.application.initialsnapshot` implementation and tests;
7. this specification for the implementation-specific delta required by #168.

Issue #138 remains the release-readiness gate. The frozen operational CSV and checksum remain external, ignored, immutable migration evidence and must not be committed.

## Confirmed transition-period semantics

The final spreadsheet was used temporarily while Lavanda Flow was being completed.

During that period:

- `Ml Disponiveis` continued to hold the previously recorded quantity;
- when stock was consumed, the operator recorded the consumed amount in a new `Retirada` column;
- `Ml Disponiveis` was not reduced to reflect those temporary withdrawals;
- `Retirada` therefore represents a migration-only adjustment needed to derive the actual opening balance.

The authoritative migration formula is:

```text
adjustedOpeningQuantity = Ml Disponiveis + Retirada
```

`Retirada` values are negative for withdrawals. Example:

```text
Ml Disponiveis = 80
Retirada = -50ml
adjustedOpeningQuantity = 30 ml
```

The adjustment is not independently auditable stock history. The spreadsheet does not provide trustworthy exact batch/date information for those temporary withdrawals, so the importer must not synthesize historical `CONSUMPTION` movements from this column.

## Source contracts

The source-specific parser must accept exactly one of these two header shapes.

### Original contract

```text
Nome do Perfume,Genero,Ml Disponiveis,Expired
```

Existing #134/#136 behavior for this shape must remain unchanged. The migration-only withdrawal adjustment is implicitly zero.

### Final frozen-snapshot contract

```text
Nome do Perfume,Genero,Ml Disponiveis,Expired,Retirada
```

`Retirada` must be the trailing fifth column. Do not accept arbitrary additional columns, reordered columns, alternative headers, or a generic schema.

For each data row, the field count must match the selected header contract exactly. Malformed rows remain deterministic validation failures rather than shifting fields silently.

## Withdrawal adjustment normalization

For the five-column contract, normalize `Retirada` as follows:

1. trim outer whitespace;
2. blank means an exact zero adjustment;
3. accept the frozen source's negative milliliter notation, including whitespace between the minus sign and the numeric value, such as `-50ml`, `- 50ml`, and `-100ml`;
4. allow a plain decimal numeric part with at most 6 fractional digits;
5. normalize the suffix as migration syntax only; it does not create a unit-conversion feature;
6. parse directly to `BigDecimal`; never use `double` or `float`;
7. reject exponent notation, malformed signs, malformed suffixes, excess precision, or other unapproved forms;
8. reject positive withdrawal adjustments because a `Retirada` cannot increase the migrated opening stock.

The importer must not introduce automatic unit conversion. `Retirada` is accepted only because this source is already defined as milliliter-based perfume/essence inventory under #134.

## Adjusted quantity semantics

Normalize the existing `Ml Disponiveis` value exactly as #134 currently requires, then compute:

```text
adjustedOpeningQuantity = availableQuantity.add(withdrawalAdjustment)
```

using `BigDecimal` only.

Rules:

- preserve `NUMERIC(19,6)` quantity semantics;
- reject excess precision rather than rounding silently;
- reject an adjusted quantity below zero;
- adjusted zero is valid;
- adjusted positive quantity is valid subject to the existing expiration/effective-date rules;
- all downstream classification and apply behavior use the **adjusted opening quantity**, not the pre-adjustment `Ml Disponiveis` value.

The existing report row `normalizedQuantity`/quantity value must represent the final adjusted opening quantity for five-column input so DRY_RUN visibly proves the value that APPLY will persist. Do not add operational row data to repository documentation merely to expose the raw withdrawal value.

## Validation codes

Keep the validation taxonomy source-specific and deterministic.

Introduce the minimum dedicated validation distinction needed for withdrawal failures rather than collapsing them into unrelated quantity errors. The implementation should use stable codes equivalent to:

- `INVALID_WITHDRAWAL_ADJUSTMENT` for malformed, positive, excess-precision, or otherwise unsupported `Retirada` syntax/value;
- `NEGATIVE_ADJUSTED_QUANTITY` when a syntactically valid withdrawal makes the final opening balance negative.

Existing validation codes and precedence must remain stable for original four-column inputs.

When a row has multiple discoverable problems, preserve the current parser's deterministic first-error behavior unless changing it is required to satisfy an existing contract.

## Expiration behavior after adjustment

Existing #134 expiration semantics still apply, but positive/zero classification is based on the adjusted opening quantity.

Therefore:

- adjusted quantity greater than zero requires a valid expiration;
- adjusted quantity equal to zero may have blank expiration and creates no batch/movement;
- adjusted positive quantity whose expiration precedes the explicit effective date is rejected;
- no effective date is backdated automatically.

Do not let the pre-adjustment quantity force an opening-stock classification when the adjusted result is zero.

## Apply behavior

Do not create a separate application path for withdrawal-aware rows.

Reuse the existing #136 import plan and application flow:

```text
parse full file
-> normalize all fields
-> calculate adjusted opening quantities
-> validate full file
-> resolve duplicate names
-> verify APPLY initialization guard
-> create catalog items
-> create opening stock for adjusted positive rows
-> commit once
```

For an adjusted positive row:

- create one active catalog item through the existing public catalog contract;
- call the existing stock-receipt path once;
- persist one opening batch whose initial/current quantity equals the adjusted opening quantity;
- persist exactly one `ENTRY` movement for that same adjusted quantity;
- retain the existing pt-BR migration reason.

For an adjusted zero row:

- create the catalog item;
- create no batch;
- create no movement.

Never create a synthetic `CONSUMPTION`, adjustment, loss, supplier, lot code, production record, or reconstructed receipt/withdrawal date from `Retirada`.

## DRY_RUN and atomicity

All existing #136 safety behavior remains authoritative:

- `DRY_RUN` and `APPLY` share the same parsing/normalization/validation path;
- `DRY_RUN` performs zero writes;
- one invalid row prevents APPLY before business writes when discoverable during full-file validation;
- APPLY refuses initialized catalog state;
- APPLY remains one outer transaction across catalog items, opening batches, and ENTRY movements;
- a mid-apply failure rolls back the complete snapshot;
- PostgreSQL becomes the operational source of truth after a successful APPLY;
- normal runtime behavior never reads the CSV.

No new schema, Flyway migration, import-state table, HTTP endpoint, dependency, or runner mode is required by this correction.

## Expected implementation boundary

The smallest expected production-code delta is inside the existing initial-snapshot capability, primarily:

- `InitialInventorySnapshotParser`;
- `InitialInventoryImportValidationCode` if dedicated validation codes are required;
- immutable report/plan types only if necessary to preserve the adjusted normalized quantity contract.

Do not change `RegisterStockReceipt`, inventory balance rules, movement semantics, catalog contracts, or other modules merely to support this source syntax.

## Tests

Use synthetic CSV strings/files only. Do not commit the real frozen operational CSV or row-level operational data.

### Parser/normalization tests

Cover at minimum:

1. original exact four-column header remains accepted unchanged;
2. final exact five-column header with trailing `Retirada` is accepted;
3. any other header/extra column remains rejected;
4. blank `Retirada` -> zero adjustment;
5. `80` plus `-50ml` -> `30.000000`-equivalent adjusted quantity;
6. whitespace variant `- 50ml` is accepted;
7. decimal withdrawal values preserve exact `BigDecimal` semantics;
8. positive withdrawal adjustment is rejected;
9. malformed suffix/value is rejected deterministically;
10. excess withdrawal precision is rejected;
11. adjusted zero becomes `CATALOG_ONLY`;
12. adjusted quantity below zero is rejected;
13. expiration-required behavior is evaluated from adjusted quantity;
14. existing name/reference normalization and duplicate disambiguation remain unchanged;
15. deterministic row ordering/reporting remains unchanged.

### PostgreSQL/Testcontainers integration

Extend the existing initial-import integration coverage to prove at minimum:

1. five-column DRY_RUN performs zero writes;
2. five-column APPLY persists adjusted positive quantity as both batch balance and opening `ENTRY` quantity;
3. adjusted zero creates catalog-only state;
4. invalid withdrawal input produces zero business writes;
5. no `CONSUMPTION` movement is created from `Retirada`;
6. original four-column APPLY behavior remains green;
7. initialization guard, rollback, and transaction participation remain unchanged.

The complete backend suite and Spring Modulith verification must remain green.

## Documentation delta

Update the smallest durable migration documentation necessary to prevent the now-known final source contract from conflicting with #134/#136.

At minimum, update the relevant migration specification/operational documentation so it clearly distinguishes:

- the original four-column mapping;
- the final optional trailing `Retirada` migration adjustment;
- the adjusted opening-balance formula;
- the prohibition on fabricated historical withdrawal movements.

Do not rewrite unrelated historical documentation or insert the real CSV contents/hash/path into versioned source unless another issue explicitly requires it.

## Acceptance criteria

- [ ] Original four-column import behavior remains backward-compatible.
- [ ] Exact five-column final snapshot with trailing `Retirada` is accepted.
- [ ] Blank withdrawal adjustment is zero.
- [ ] Frozen negative `ml` syntax, including sign whitespace, is parsed exactly with `BigDecimal`.
- [ ] Positive/malformed/excess-precision withdrawal adjustments are rejected deterministically.
- [ ] Final opening quantity equals `Ml Disponiveis + Retirada`.
- [ ] Adjusted negative balance is rejected before APPLY writes.
- [ ] Adjusted zero produces catalog-only state.
- [ ] Adjusted positive quantity drives existing expiration validation and opening-stock behavior.
- [ ] DRY_RUN reports the adjusted quantity and remains write-free.
- [ ] APPLY persists the adjusted quantity consistently in batch balance and opening ENTRY movement.
- [ ] No synthetic withdrawal/consumption history is created.
- [ ] Existing #134 name, reference, duplicate, quantity, and expiration normalization remains unchanged except where quantity classification necessarily uses the adjusted value.
- [ ] APPLY remains atomic and guarded against initialized catalog state.
- [ ] No schema, new dependency, public HTTP endpoint, recurring import feature, frontend change, or unrelated production code is introduced.
- [ ] Synthetic unit/integration coverage proves both source contracts and edge cases.
- [ ] Spring Modulith verification remains green.
- [ ] `./mvnw verify` succeeds.
- [ ] The frozen external source remains unchanged so #138 can rerun the exact same snapshot after #168 merges.

## Out of scope

- editing/preprocessing the frozen CSV;
- recurring or generic CSV import;
- reconstructing historical withdrawal dates or source batches;
- importing `Retirada` as movement history;
- unit conversion;
- frontend changes;
- production changes;
- supplier reconstruction;
- minimum-shelf-life policy;
- release preparation itself.

## Final validation

Before finishing implementation:

1. run `./mvnw verify` from `backend/`;
2. review the complete `git diff` for source-contract scope and module-boundary violations;
3. run `git diff --check`;
4. run `git status --short`;
5. confirm no file under `operational-data.local/` appears in Git status or the diff.
