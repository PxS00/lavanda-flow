# Issue #229 — Model finished products and shared fragrance references

## Status

Implementation specification; validation evidence is recorded in the linked pull request.

## Source of truth

This specification refines GitHub issue #229 for implementation. The following remain authoritative:

- GitHub issue #229 for product intent, scope, and acceptance criteria;
- `AGENTS.md` for repository engineering constraints;
- `docs/architecture/architecture.md` for module ownership and boundaries;
- `docs/architecture/backend-structure.md` for package organization;
- `docs/architecture/data-model.md` for inventory persistence invariants;
- the current backend code and generated OpenAPI contract for existing behavior;
- the current frontend code and tests for existing Angular composition.

If an implementation detail is not specified here, preserve the existing behavior and choose the smallest change that satisfies the issue.

## Objective

Extend the existing catalog `InventoryItem` model so the system can represent:

- formulated finished products held in bulk before bottling;
- separately stocked packaged presentations of a fragrance;
- stable fragrance references shared by a canonical essence and derived finished products;
- operational product-gender classification using the source values `M`, `F`, `C`, `M/C`, and `F/C`.

The implementation must preserve the single catalog/inventory model, existing production lot-code behavior, exact stock invariants, and module boundaries.

## Context

The first real v0.6.0 initial-inventory import revealed that the legacy spreadsheet contains formulated perfume stock measured in milliliters. Those rows are not raw essences.

The same fragrance may exist as:

- bulk formulated stock;
- a 30 ml packaged presentation;
- a 50 ml packaged presentation;
- a premium or otherwise distinct packaged presentation.

These are separate physical stock items but share fragrance lineage through `essenceReference`.

The existing implementation has three incompatible assumptions:

- `Category` has no `FINISHED_PRODUCT`;
- `essenceReference` is allowed only for `ESSENCE`;
- the database enforces global reference uniqueness, preventing multiple finished products from sharing a fragrance reference.

## Domain decisions

### Single inventory-item model

Keep one catalog aggregate/model:

```text
InventoryItem -> Batch -> StockMovement
```

Do not introduce `Perfume`, `BodySplash`, `Moisturizer`, `PackagingProduct`, or another product aggregate.

### Finished-product category

Add the stable category wire value:

```text
FINISHED_PRODUCT
```

It applies to both bulk formulated stock and packaged presentations.

The category does not identify the commercial family or presentation by itself.

### Product family and presentation

Keep these concepts separate:

- `category`: stock classification; `FINISHED_PRODUCT` for the products in this issue;
- `productionTypeCode`: optional stable three-uppercase-letter family code used by the existing lot-code contract; for example, an approved perfume code may be used by the operational snapshot;
- `unitOfMeasure`: physical stock unit; bulk perfume uses `MILLILITER`, while independently stocked packaged presentations commonly use `UNIT`;
- item name/description: human-readable presentation identity;
- `lotCode`: physical batch identity, owned by the inventory module.

Do not introduce an ambiguous free-form `type` field. Do not encode gender or presentation into the generated lot code.

### Product gender

Add optional catalog metadata named `gender` for fragrance and fragrance-bearing items.

The canonical values and wire values are:

| Wire value | Meaning |
| --- | --- |
| `M` | Masculine |
| `F` | Feminine |
| `C` | Shared |
| `M/C` | Shared with a masculine tendency |
| `F/C` | Shared with a feminine tendency |

The values must be preserved exactly across the source report, persistence, public API, and frontend transport. Input normalization may remove surrounding whitespace and normalize spacing around the slash, but the stored/API value must be canonical.

`gender` is allowed only for `ESSENCE` and `FINISHED_PRODUCT`. It is optional for ordinary catalog registration because not every item is fragrance-bearing. The corrected perfume import in #231 must require it for every valid source row.

Gender is descriptive catalog metadata, not part of the generated production lot code and not a replacement for structured fragrance profiles from #228.

### Stable fragrance reference

Keep the existing `essenceReference` field name and its wire/API name.

Valid values remain:

```text
001 through 999
```

`000` remains reserved for the generated no-fragrance lot-code case and is never valid item metadata.

Reference rules:

- a canonical `ESSENCE` may have one non-null reference;
- canonical `ESSENCE` references remain unique;
- a canonical essence with an assigned reference cannot change category, preserving non-recycling;
- multiple `FINISHED_PRODUCT` items may share a reference;
- an assigned reference remains immutable;
- submitting the same assigned value again is an accepted replay;
- changing or removing an assigned value is rejected;
- existing null references remain valid.

A shared reference identifies fragrance lineage. It does not merge stock, batches, movements, product presentations, or catalog items.

## Backend implementation contract

### Domain

Update `catalog.domain.Category` with `FINISHED_PRODUCT`.

Add a typed product-gender concept with the five canonical wire values. The implementation may use an enum or equivalent value type, but invalid values must be rejected before persistence.

Update `InventoryItem` so that:

- `FINISHED_PRODUCT` is valid;
- `essenceReference` is accepted for `ESSENCE` and `FINISHED_PRODUCT`;
- `gender` is accepted only for `ESSENCE` and `FINISHED_PRODUCT`;
- category changes cannot leave assigned reference or gender metadata on an ineligible category;
- reference and production-type immutability behavior remains unchanged;
- no stock, batch, movement, formula, execution, or genealogy rule moves into the catalog domain.

### Application and public module API

Keep the importer in the `inventory` module.

Extend the public catalog API only with the smallest contract required to create a finished product from another module. The public contract must not expose:

- catalog JPA entities;
- catalog repositories;
- catalog infrastructure;
- catalog application commands;
- internal persistence types.

The finished-product registration contract must carry:

- name;
- physical unit;
- optional description where the existing registration policy supports it;
- canonical gender value;
- stable `essenceReference`;
- stable `productionTypeCode`.

The existing `registerEssence(String name)` contract remains compatible unless the implementation proves that a strictly smaller safe replacement is possible.

The catalog registration adapter remains responsible for applying catalog domain rules. The inventory importer must not construct catalog entities or bypass the catalog registration policy.

### Read models and HTTP

Update catalog application results, persistence mapping, HTTP request/response DTOs, and OpenAPI inference/documentation as required.

The API must expose:

- `FINISHED_PRODUCT` as a category wire value;
- `gender` using exactly `M`, `F`, `C`, `M/C`, or `F/C`;
- existing `essenceReference` and `productionTypeCode` fields without renaming them.

Existing routes remain unchanged. Existing items with null optional metadata remain valid.

Do not add a new product-specific API family or a second finished-product resource.

### Persistence

Add one append-only Flyway migration after the current latest migration. The expected migration is:

```text
V15__align_finished_product_reference_constraints.sql
```

The migration must:

- add a nullable `gender`/physical `product_gender` column using the repository naming convention;
- enforce the five allowed gender values;
- enforce gender category eligibility;
- replace the current essence-reference category check so it allows `ESSENCE` and `FINISHED_PRODUCT`;
- drop the current globally unique essence-reference index;
- create a partial unique index for non-null references where `category = 'ESSENCE'`;
- preserve reference and production-type immutability triggers;
- preserve protection against deleting an item with an assigned stable reference;
- avoid changing historical migrations.

The migration must be valid for the existing v0.6.0-compatible schema and must not rewrite operational data.

### Production lot compatibility

Do not change the lot format:

```text
TTT-EEE-LLL-MM-YYYY
```

For a finished-product output with assigned metadata, the existing production flow must continue to use that output item's persisted `productionTypeCode` and `essenceReference`.

For an item without a fragrance reference, the existing `000` behavior remains unchanged.

Gender must not affect lot allocation or lot-code generation.

## Frontend implementation contract

Update the existing catalog feature only.

Required behavior:

- show `FINISHED_PRODUCT` as “Produto finalizado” in user-facing Portuguese;
- allow registration of a finished product with `MILLILITER` or `UNIT`;
- allow selecting/displaying the five gender values with clear Portuguese labels;
- preserve the exact API wire values;
- display gender and existing production metadata when returned by the backend;
- keep optional metadata absence explicit;
- use the existing catalog data-access service and DTOs;
- do not create a parallel frontend product model;
- do not calculate stock, expiration, FEFO, production quantities, or lot codes in Angular.

Frontend validation may reject malformed shape, but backend validation remains authoritative.

## Consistency rules for the follow-up import

Issue #231 consumes this catalog contract.

The corrected source header is:

```text
Nome do Perfume,Genero,Ml Disponiveis,Expired,Retirada,EssenceReference,ProductionTypeCode,LotCode
```

The importer will map:

```text
Genero              -> gender
EssenceReference    -> essenceReference
ProductionTypeCode  -> productionTypeCode
LotCode             -> opening batch lotCode
```

Every valid imported row is a `FINISHED_PRODUCT` with `MILLILITER`. The identity key remains:

```text
productionTypeCode + essenceReference
```

For the same fragrance reference, conflicting normalized gender values must be rejected before APPLY. The importer must never infer any missing metadata from the product name.

The real CSV, enriched metadata, lot values, checksum, credentials, and database dumps remain outside source control.

## Module boundaries

- `catalog` owns item identity, category, optional gender, stable references, and catalog persistence.
- `inventory` owns the initial snapshot use case, batches, quantities, movements, and transaction orchestration.
- `inventory` may call only public catalog APIs.
- `production` continues to consume the existing public catalog reference lookup.
- No new module is introduced.
- No event is introduced; synchronous public API calls remain sufficient.

Spring Modulith verification must remain green.

## Tests

### Domain and application

Cover:

- `FINISHED_PRODUCT` acceptance;
- bulk product with `MILLILITER`;
- packaged presentation with `UNIT`;
- all valid gender values;
- invalid and blank gender values where required;
- gender rejection on ineligible categories;
- valid references `001` through `999`;
- rejection of `000`;
- reference assignment, unchanged replay, change rejection, and removal rejection;
- multiple finished products sharing one reference;
- duplicate canonical essence reference rejection;
- conflicting metadata rejection where applicable.

### PostgreSQL/Testcontainers

Prove:

- migration from the current schema;
- fresh schema migration;
- gender check constraints;
- category eligibility;
- partial uniqueness for canonical essences;
- repeated finished-product references;
- immutable assigned references;
- assigned-reference deletion protection;
- legacy null metadata compatibility.

### HTTP/OpenAPI

Cover:

- category serialization;
- gender serialization using exact wire values;
- registration and detail round trips;
- invalid payload behavior;
- existing routes and legacy null fields.

### Production compatibility

Prove that:

- finished-product metadata is visible to the existing production reference lookup;
- generated lot prefixes continue to use persisted output metadata;
- no-fragrance `000` behavior remains intact;
- gender does not change generated lot codes.

### Frontend

Cover:

- category option and Portuguese presentation;
- gender options and exact request values;
- registration of bulk and packaged finished products;
- detail/list rendering;
- null metadata state;
- backend validation error handling;
- existing catalog behavior remains intact.

## Documentation

Update only documentation required by the changed contract:

- this specification;
- relevant catalog/domain documentation;
- API/OpenAPI documentation if inferred output is insufficient;
- the initial-import documentation when #231 is implemented.

Do not add the real operational CSV, its lot data, checksum, or credentials to versioned documentation.

## Acceptance checklist

- [ ] `FINISHED_PRODUCT` is available across domain, persistence, API, OpenAPI, and Angular.
- [ ] Bulk formulated products support `MILLILITER`.
- [ ] Packaged presentations support `UNIT`.
- [ ] `gender` persists and preserves `M`, `F`, `C`, `M/C`, and `F/C`.
- [ ] Gender is valid only for `ESSENCE` and `FINISHED_PRODUCT`.
- [ ] Corrected perfume import can persist gender, reference, production type, and lot metadata through public module contracts.
- [ ] `essenceReference` remains `001`–`999`, with `000` reserved and non-assignable.
- [ ] Canonical essence references remain unique.
- [ ] Finished-product references may be reused.
- [ ] Assigned references remain immutable in production code and PostgreSQL.
- [ ] Existing null metadata remains valid.
- [ ] Generated lot behavior remains unchanged.
- [ ] No product-specific aggregate, module, or frontend classification model is introduced.
- [ ] No existing stock or production history is rewritten.
- [ ] Spring Modulith verification passes.
- [ ] `./mvnw verify`, `pnpm lint`, `pnpm test`, and `pnpm build` pass.

## Out of scope

- Initial CSV re-import implementation details owned by #231.
- Grouped stock workspaces (#230).
- Filling/bottling execution (#232).
- Structured fragrance profiles (#228).
- Sales, orders, customers, pricing, payments, and checkout.
- Generic unit conversion.
- New production, formula, or genealogy behavior.
- Barcode/QR support.
- New dependencies, events, caches, or state-management libraries.
- General catalog maintenance workflow owned by #222.

## Implementation sequence

1. Confirm the current `develop` baseline and read the current catalog contracts/tests.
2. Add the typed gender/category domain behavior and focused unit tests.
3. Add the append-only PostgreSQL migration and Testcontainers constraint tests.
4. Update catalog application/public contracts and persistence adapters.
5. Update HTTP DTOs/OpenAPI behavior and backend integration tests.
6. Update Angular DTOs, display metadata, registration/detail UI, and frontend tests.
7. Run Spring Modulith, backend, frontend lint, frontend tests, and frontend build.
8. Review the complete diff for unrelated changes before opening the issue PR.

Do not implement #231, #230, or #232 in this branch.
