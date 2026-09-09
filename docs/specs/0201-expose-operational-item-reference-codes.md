# Issue #201 — Expose operational item reference codes

## Status

Approved implementation specification for issue #201.

The GitHub issue remains the source of truth for Objective, Context, Scope, Acceptance Criteria, Constraints, and Out of Scope. This document fixes the concrete frontend decisions required to expose existing backend-owned reference metadata without inventing a frontend taxonomy or changing production/inventory behavior.

## Branch and baseline

- Branch: `feature/201/expose-operational-item-reference-codes`
- Base: `develop`
- Baseline commit: `18940b30b136b792b10bc697bd30c6d62e205f52`
- Visual foundation #199: merged.
- Contextual guidance #200: merged.

## Objective

Make the existing persisted `essenceReference` and `productionTypeCode` values easy to scan at the current catalog, inventory, receipt, and production decision points where item names alone may be ambiguous.

The item name remains the primary human-readable identity. Reference values are compact secondary operational metadata.

This issue is presentation/read-path work only. It must not define, assign, infer, recycle, search by, or mutate reference codes beyond existing catalog registration behavior.

## Source of truth and existing contract

Apply, in order:

1. GitHub issue #201;
2. root `AGENTS.md`;
3. `frontend/AGENTS.md`;
4. `docs/specs/0152-manage-production-setup.md`;
5. current `develop` frontend implementation;
6. existing catalog API/DTO contract and tests.

The current `InventoryItemDto` already contains:

```ts
readonly essenceReference: string | null;
readonly productionTypeCode: string | null;
```

The existing catalog endpoints already return those values in list/search and `GET /api/v1/inventory-items/{inventoryItemId}` responses.

The existing registration request already sends the same wire fields. Preserve their names and transport values exactly.

Do not add a new backend endpoint, migration, column, enum, taxonomy, or frontend-owned reference store.

## Current-state findings

The current frontend has the required data contract but underuses it operationally:

- catalog list/search cards receive both values but do not render them;
- item detail already renders both values as plain metadata rows;
- item registration already accepts both fields but only explains input shape;
- the shared inventory-item selector receives full `InventoryItemDto` objects but search results and selected state show only name/unit/status;
- receipt and production formula setup both reuse that selector, so improving it has high leverage without duplicating identity logic;
- production formula list already resolves output items through `InventoryItemApiService.getById`, therefore output references are already available without new API work;
- production registration already loads full catalog item DTOs for formula outputs and ingredients, therefore reference values are available in the current execution/review flow;
- inventory operational overview uses an inventory-specific DTO that does not contain reference metadata, while the route item can already be read through the existing catalog `getById` endpoint;
- genealogy DTOs do not contain reference metadata and rendering codes there would require extra catalog lookups for potentially many nodes.

These findings mean no backend blocker exists for the primary #201 surfaces.

## Presentation model

### Primary vs secondary identity

Always keep:

1. item name first and most prominent;
2. reference metadata second and visually quieter;
3. unit/category/status after or alongside operational context as appropriate.

Do not replace item names with codes.

### Labels

Use concise pt-BR labels in compact contexts:

- `Ref. essência` for `essenceReference`;
- `Cód. produção` for `productionTypeCode`.

Use the fuller existing labels in low-density detail/form contexts when appropriate:

- `Referência da essência`;
- `Código do tipo de produção`.

The code type must be communicated in text, not by color alone.

### Exact values

Render persisted strings exactly as returned by the backend.

Examples such as `027` or `BHC` are test fixtures/examples only. Do not hard-code them into production branching, dictionaries, defaults, labels, or inferred mappings.

Preserve leading zeroes in essence references.

### Null handling

Compact reference presentation must omit an unassigned value rather than rendering fake placeholders such as `000`, `---`, `N/A`, or invented codes.

When both values are null, the compact reference group should render nothing.

The item detail page may continue to show an explicit pt-BR absence state because it is a full metadata inspection screen.

## Reusable reference presentation

Repeated compact rendering across catalog list, shared selectors, inventory context, and production surfaces justifies one small catalog-owned presentational component.

Preferred shape:

```text
frontend/src/app/features/catalog/ui/inventory-item-reference-metadata/
```

with a component conceptually equivalent to:

```text
InventoryItemReferenceMetadata
```

Responsibilities only:

- accept `essenceReference: string | null`;
- accept `productionTypeCode: string | null`;
- render the assigned values with explicit pt-BR type labels;
- render nothing when both are null;
- remain selectable/copyable text;
- wrap naturally on notebook/tablet widths;
- expose no business rules, inference, validation, mutation, or HTTP behavior.

Do not add variants, configuration objects, taxonomy services, or a generic badge framework unless current implementation proves they are necessary.

Use existing #199 tokens/Material system colors. Do not introduce raw brand-color duplication.

## Catalog list/search

Render compact reference metadata inside each existing item card when at least one field is assigned.

Requirements:

- item name remains the card title;
- category/unit/status behavior remains unchanged;
- references are secondary metadata;
- both values may appear together;
- one may appear alone;
- neither should create empty visual chrome;
- search/filter/query behavior remains unchanged.

Do not add search-by-reference client behavior, extra filters, or query parameters. Search-by-code is explicitly out of scope.

## Item detail

Refine the existing reference rows so assigned codes are easier to scan and copy without making them look editable.

Requirements:

- preserve both existing fields;
- preserve explicit absence text for null values;
- use clear pt-BR labels;
- use stable visual treatment aligned with the compact reference pattern where useful;
- do not add edit controls or imply existing assigned metadata can be changed from this screen.

No new catalog update contract exists; do not invent one.

## Item registration

Keep the existing request mapping and validators unchanged unless a defect is discovered independently.

Improve helper text so the operator understands that the values are stable references and should be assigned deliberately.

Guidance must remain descriptive rather than becoming a frontend source of truth.

Acceptable guidance concepts:

- essence reference is an optional stable operational reference for an essence item;
- production type code is an optional stable production reference;
- once assigned, these values are treated as stable metadata by the system.

Do not add:

- code pickers;
- dropdown taxonomies;
- suggested values;
- automatic uppercase/number generation unless already existing behavior;
- hard-coded examples such as `BHC` as a default or recommendation.

Existing shape hints (`001–999`, three uppercase letters) may remain because they already describe the accepted contract.

## Shared inventory-item selector

This is a primary #201 surface because it is reused by receipt and formula setup.

For each search result:

- show item name first;
- show assigned references compactly beneath/alongside the existing unit/status metadata;
- preserve click/keyboard behavior and result ordering;
- null references must not create fake placeholders.

For selected state:

- show the selected item name first;
- include assigned references as compact secondary identity;
- preserve existing clear/search behavior.

Do not change the API search query to search by code.

## Receipt flow

The receipt page already uses the shared inventory-item selector.

The selector enhancement is sufficient for item choice and selected-item identity unless implementation reveals another ambiguous receipt summary.

Do not duplicate the same reference chips repeatedly across the receipt form when the selected-item block already establishes identity.

Receipt payloads, quantity handling, supplier behavior, batch fields, and audit semantics remain unchanged.

## Inventory operational workspace

The page should establish reference metadata once near the selected item identity so child sections (batches, minimum stock, FEFO, maintenance, history) do not repeat codes on every row.

The inventory overview DTO does not contain reference metadata. Use the existing catalog read contract for the same route item rather than extending the inventory DTO or inventing local values.

Implementation requirements:

- read the current item via `InventoryItemApiService.getById(inventoryItemId)` using the existing route id;
- keep catalog reference loading separate enough that no fabricated code is shown;
- preserve all existing inventory APIs and backend-authoritative stock behavior;
- render compact assigned references beside/under the item name in the operational identity area;
- do not add codes to every batch/history row if the parent item identity is already visible;
- no FEFO/expiration/stock calculations may be added.

A reference-metadata read failure must not be converted into fake null values presented as authoritative absence. Handle the supplementary read explicitly while keeping the operational stock flow understandable.

Do not change backend inventory contracts merely to avoid the existing catalog read.

## Stock maintenance

Stock maintenance is item-scoped from the operational page and already displays batch identity.

Do not add duplicate item-reference data inside every maintenance dialog unless the current implementation cannot otherwise identify the acted-on item.

The parent operational item identity should be the primary reference-code surface.

Do not modify maintenance request semantics or eligibility rules.

## Production formula list

The formula list already resolves each output item to a full `InventoryItemDto`.

Render output item references compactly in each formula card.

Preserve:

- output item name as primary;
- output quantity/unit;
- formula navigation;
- existing API requests and loading/error behavior.

Do not derive codes from formula data.

## Production formula form

The shared item selector enhancement automatically applies to:

- output item selection;
- ingredient selection;
- loaded selected values in edit mode.

Use that shared pattern rather than duplicating separate code rendering inside each formula field unless a selected summary still lacks identity.

Formula payloads and business semantics remain unchanged.

## Production registration / execution

Full `InventoryItemDto` objects are already loaded for output items and ingredient contexts.

Expose assigned references at decision points where the operator verifies identity:

- formula/output selection context;
- selected output summary;
- ingredient/source-batch sections;
- review before confirmation;
- backend-confirmed result/consumption summary where item identity is shown.

Do not flood every repeated row. Prefer one reference group next to each item identity block.

Preserve exact-batch selection, payloads, review state, system-authoritative validation, generated lot behavior, and post-success refresh semantics.

Do not infer production type from formula/category/name.

## Genealogy

Do not add N+1 catalog lookups solely to decorate every genealogy node in #201.

The current genealogy contract provides item name/category/unit/lot context but not reference metadata. The page already has strong batch identity, and mass catalog lookups would add complexity and latency disproportionate to this issue.

Therefore:

- keep genealogy behavior unchanged unless reference metadata is already available from an existing loaded catalog item without extra fan-out;
- do not extend the backend genealogy contract in this issue;
- if real operator acceptance later proves genealogy codes are necessary, raise a focused contract/performance follow-up rather than hiding an N+1 implementation here.

This does not block #201 because the current primary production execution/setup surfaces already have full catalog item DTOs.

## Styling and accessibility

Reference presentation must:

- use existing #199 semantic/Material tokens;
- remain lower emphasis than item names;
- remain readable at notebook/tablet widths;
- wrap rather than clip;
- not depend on hover;
- not depend on color alone;
- keep text selectable/copyable where practical;
- preserve keyboard behavior in buttons/options/selectors;
- avoid icon-only code types.

A native `<code>` element or equivalent semantic text treatment is acceptable if styled consistently and legibly.

Do not add an icon library or new dependency.

## Backend-authority boundary

This issue must not introduce frontend ownership of reference semantics.

Specifically, do not:

- generate essence references;
- generate production type codes;
- normalize a persisted response into another displayed code;
- map item names/categories to a code dictionary;
- infer `BHC` or any other code;
- decide whether a metadata mutation is allowed;
- recycle or reserve references;
- recompute production lot codes;
- recompute FEFO, expiration, available stock, allocation, genealogy, or balances.

Display the values returned by the catalog contract exactly.

## Expected implementation footprint

Likely touched areas:

```text
frontend/src/app/features/catalog/ui/inventory-item-reference-metadata/*
frontend/src/app/features/catalog/ui/inventory-item-selector/*
frontend/src/app/features/catalog/pages/inventory-item-list-page/*
frontend/src/app/features/catalog/pages/inventory-item-detail-page/*
frontend/src/app/features/catalog/pages/inventory-item-registration-page/*
frontend/src/app/features/inventory/pages/inventory-item-operational-page/*
frontend/src/app/features/production/pages/production-formula-list-page/*
frontend/src/app/features/production/pages/production-formula-form-page/*  # mainly tests if selector handles presentation
frontend/src/app/features/production/pages/production-registration-page/*
```

Receipt files should only change if the shared selector does not fully cover the required selected-item context.

Do not edit backend Java, Flyway, schema, Compose/runtime files, auth/session code, routes, DTO wire names, or dependencies.

## Testing strategy

Add focused tests for visibility and preservation, not brittle full-template snapshots.

At minimum cover:

### Reusable reference presentation

- renders both assigned values with explicit code-type labels;
- renders only the assigned value when the other is null;
- renders no fake placeholder when both are null;
- preserves exact strings including leading zeroes and values such as `BHC` supplied only as fixture data.

### Catalog

- list/search card shows backend-provided reference metadata;
- null metadata produces no compact code placeholder;
- item detail still shows both fields and explicit absence states;
- registration helper text communicates stable/deliberate assignment;
- existing registration request assertions prove DTO/wire values are unchanged.

### Selector / receipt / formula setup

- search result shows references;
- selected item shows references;
- null values remain clean;
- selection/query behavior is unchanged;
- receipt/formula consumers continue receiving the original `InventoryItemDto` object unchanged.

### Inventory operational page

- current item catalog metadata is loaded using the route item id;
- assigned references render at the item identity level;
- reference loading does not introduce duplicate stock/business calculations;
- existing inventory request behavior remains intact.

### Production

- formula list output displays persisted references;
- production registration output/ingredient/review contexts display persisted references where implemented;
- exact reference strings are preserved;
- request payloads, lot mode, allocations, decimal values, and route behavior are unchanged.

Do not weaken existing tests.

## Validation

From `frontend/`:

```bash
pnpm lint
pnpm test
pnpm build
```

Then review:

```bash
git diff --check
git diff
git status --short
```

The existing initial bundle-budget warning remains non-blocking if materially unchanged. Record it; do not expand #201 into bundle optimization.

## Acceptance mapping

#201 is complete when:

- catalog list/search exposes assigned values;
- detail keeps both values scannable and explicit when absent;
- registration explains stable assignment without changing the contract;
- shared selectors expose assigned values in receipt/formula setup;
- inventory operational item identity exposes assigned values through the existing catalog read contract;
- production setup/execution exposes references at material item-identification points;
- names remain primary and codes secondary;
- null values never become fabricated codes;
- fixture `BHC`/numeric references render exactly when returned;
- no frontend taxonomy/inference/generation exists;
- no backend/API/schema/runtime/security/route/dependency change is introduced;
- no FEFO/expiration/available-stock/production-allocation/lot-number/genealogy/balance rule is duplicated;
- `pnpm lint`, `pnpm test`, and `pnpm build` pass.
