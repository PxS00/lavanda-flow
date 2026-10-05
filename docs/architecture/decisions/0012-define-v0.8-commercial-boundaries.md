# ADR 0012 — Define v0.8 commercial module boundaries

- **Status:** Accepted
- **Date:** 2026-10-05

## Context

The approved v0.8.0 extension adds minimal customer contacts and stock-integrated orders/sales while preserving the historical V1 boundary. `catalog` already owns stable item identity and publishes immutable lookup values. `inventory` owns batches, balances, movements, FEFO, expiration, eligibility, and pessimistic stock locking. `production` demonstrates a single PostgreSQL transaction orchestrating a public inventory command.

Commercial confirmation must preserve the exact customer/product values sold, identify the batches consumed, and commit sale state and every stock movement atomically. Drafts must not hold stock. There is no requirement for payments, fiscal issuance, attachments, or external providers.

## Decision

Add two cohesive modules for the v0.8.0 implementation: `customers` owns customer contacts; `sales` owns draft orders, confirmation, confirmed sale history, immutable line snapshots, and sale-to-batch allocations. Orders and sales share one lifecycle record: a `DRAFT` order becomes a `CONFIRMED` sale, or an unconfirmed draft becomes `CANCELLED`.

### Ownership and dependencies

```text
sales ──► customers
  ├─────► catalog
  ├─────► inventory
  └─────► shared (narrow cross-cutting facilities only)

catalog, inventory, production, suppliers, customers ──► shared (where needed)
```

Arrows mean “depends on.” `customers` does not depend on `sales`; existing modules do not depend on either new module. `sales` uses only public contracts and immutable values from other modules. It never imports their domain objects, repositories, JPA entities, or infrastructure. `inventory` has no sales dependency: it accepts opaque audit-reference values and remains the sole owner of stock policy and persistence.

`sales` resolves customer existence/active state through a public `customers` lookup. It uses the existing public `catalog.InventoryItemDetailsLookup` contract to validate stable product ID, category, unit, and active state. Only active `FINISHED_PRODUCT` items using `MILLILITER` or `UNIT` are eligible; every stocked presentation remains its own catalog identity and quantities use exactly that identity's unit.

### Inventory contract and transaction

The sales application use case owns one PostgreSQL transaction for confirmation and synchronously calls a narrow public inventory sale-withdrawal contract. The contract accepts exact item quantities and opaque `{referenceType, referenceId, referenceLineId}` audit values; it returns exact FEFO batch allocations and movement IDs. Inventory locks product identity and eligible batches using its existing concurrency policy, validates current active state, expiration (`expiresAt <= today` is expired), and sufficient eligible stock using the application `Clock`, then persists balances and immutable movements. It does not reveal or delegate its internal repositories/entities.

Sale confirmation, inventory withdrawals/movements, allocation rows, immutable sale snapshots, and final state commit or roll back together in the same PostgreSQL transaction. The public inventory operation joins the caller transaction. A failed validation or insufficient eligible stock leaves the order `DRAFT`, with no withdrawal or partial movement. No saga, event continuation, reservation, distributed transaction, or automatic application retry is introduced.

The order row is locked before confirmation. Inventory item IDs are processed in stable UUID order; inventory applies its item/batch lock ordering. The order identity is the idempotency key: confirming an already confirmed order returns its stored result and never calls inventory again. Competing confirmation requests serialize on the order row. A retry after a rolled-back failure may retry the same draft. Database deadlock/timeout failures roll back and surface through the existing error contract; any retry remains an explicit client retry of the same order.

### History and movement references

At confirmation, sales snapshots customer name/phone/email and each product's stable ID, display name, unit/presentation, exact quantity, entered unit price, and rounded line amount. Later catalog/customer edits do not change confirmed history. Customer and product references are retained; records with sales history are deactivated or made inactive, never deleted.

Inventory movements stay immutable and inventory-owned. Their opaque source reference identifies the sale and line; the sales-owned allocation row stores sale line, batch ID, exact quantity, and movement ID. No module parses item names or lot strings to recover identity or genealogy. A uniqueness constraint on a referenced sale-line/batch consumption provides defense against duplicate movement creation; the locked sale state is the primary retry guard.

### State and correction semantics

- `DRAFT`: editable; no stock reservation, movement, or availability guarantee.
- `DRAFT -> CONFIRMED`: atomic confirmation; this is the only normal transition that changes stock.
- `DRAFT -> CANCELLED`: cancels an unfulfilled order; no stock effect.
- `CONFIRMED`: immutable sale history; cannot transition to cancelled or be deleted.

An actual physical return or confirmed-data correction is a separate audited inventory correction, not cancellation. Returning stock requires an operator to verify physical receipt and saleable condition; inventory decides eligibility and expiration. Any accepted return is a new positive movement against the original allocated batch and carries the sale-line reference. Corrections to confirmed price/customer/product/quantity never rewrite the old sale or its movements. Payment refunds and a full returns-management workflow are outside this scope.

## Persistence and API consequences

Later implementation adds customer and sales relational tables through new forward-only Flyway migrations after the current V16. Existing migration history and data are never rewritten. Sale allocations refer to stable UUIDs; no cross-module JPA relationships are permitted. Inventory movement audit-reference columns are nullable/additive so existing movements and HTTP responses retain their current shape. Constraints and indexes are added with the owning implementation slices after checking existing data and rollout order. Rollback is an application rollback with additive schema retained; any irreversible business data recovery requires a verified PostgreSQL backup, not destructive down migrations.

New authenticated JSON contracts live under `/api/v1/customers` and `/api/v1/sales`; existing contracts and the Spring Security session/CSRF boundary remain unchanged. Routes and DTO names are finalized in their implementation issues. Angular calls these contracts through feature data-access services and never accesses PostgreSQL or Supabase APIs directly.

## Alternatives considered

### Put customers and sales inside `catalog` or `inventory`

Rejected. Customer identity is not inventory metadata, and order/sale lifecycle is not a stock balance. This would blur existing ownership and make future changes depend on unrelated internals.

### Create separate `orders`, `payments`, `fiscal`, or `crm` modules

Rejected. Draft order and confirmed sale are one lifecycle and one transaction. The excluded capabilities have no independent approved requirement.

### Let the frontend call existing withdrawal endpoints

Rejected. Separate HTTP withdrawals cannot atomically persist a sale and all allocations, and frontend retries could duplicate stock changes. Inventory needs one public, transaction-joining operation invoked by the sales application service.

### Reserve stock when saving a draft

Rejected. No hold lifecycle or reservation requirement exists, and stale drafts would complicate availability. Inventory changes only on successful confirmation.

### Make sales infer batch identity from product names or lot codes

Rejected. Catalog item IDs and inventory batch/movement IDs are the existing stable identities; strings cannot safely represent allocation or genealogy.

## Consequences

- `customers` and `sales` become the two planned v0.8.0 modules, while V1 documentation remains historical and unchanged in meaning.
- Sales owns the business transaction and history; inventory owns all stock decisions and effects.
- Confirmation is synchronous and atomic in PostgreSQL, which keeps retries and rollback understandable for a single-operator workflow.
- Inventory gains one public sale operation and opaque movement-reference data, but no dependency on sales.
- Existing HTTP contracts remain stable; only additive routes/schema are introduced by later implementation issues.

## Deferred implementation details

Exact route/DTO names, table and column names, whether customer contact normalization uses a value object, concrete lock query implementation, index names, and HTTP status mappings are implementation details governed by this decision and the existing API error contract. No structural ownership decision remains open.
