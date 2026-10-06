# v0.8.0 Product Scope: Customers, Orders, and Sales

## Objective

Extend Lavanda Flow's operational inventory system with minimal customer contacts, draft orders, and confirmed sales that withdraw eligible finished-product stock with auditable batch traceability.

This is a versioned extension after v0.7.1. The historical [V1 scope](scope-v1.md) remains accurate: sales and fiscal capabilities are outside V1 and are not retroactively added to it.

## Implementation status

Issue #260 implements authenticated customer contact maintenance/search and its public lookup.
See the [delivered customer contract](../specs/0260-manage-customer-contacts.md) and
[operator workflow](customer-contacts.md). Issue #261 implements optional-customer draft orders, exact entered pricing, authoritative totals and authenticated capture/edit/list/detail workflows. See the [draft contract](../specs/0261-register-customer-orders.md) and [operator workflow](customer-orders.md). Confirmation, cancellation, historical snapshots and inventory integration below remain planned.

## Included capabilities

### Customers

Operators can create, search, view, edit, activate, and deactivate customer contact records containing only a required name and optional phone and email. Name is trimmed and limited to 160 characters. Phone is optional, accepts a leading `+` and common display separators, and is stored normalized with 7–15 digits; email is optional, trimmed, syntax-validated, and limited to 254 characters. Neither contact method is required, and neither is unique. Search is case-insensitive across name and normalized contact values, paginated using the existing 20/100 limits. Deactivation prevents new or confirmed orders but does not delete a customer or its historical sales.

Confirmed sales preserve the customer identifier and the name, phone, and email values displayed at confirmation. Later contact edits do not rewrite a past sale.

### Orders and sales

An order starts as a draft and becomes a sale only when an operator confirms it. Drafts do not reserve stock. A draft can be edited or cancelled without stock effects. Customer association on a draft is optional. A supplied customer must be active at each save. Confirmation withdraws all line quantities using backend-authoritative FEFO and either confirms the complete order or changes nothing. The confirmed sale, its line values, its batch allocations, and its inventory movements form one auditable history.

### Eligible products and quantities

Only active catalog items with category `FINISHED_PRODUCT` are sellable. Existing bulk finished-product identities use `MILLILITER`; separately stocked packaged presentations use `UNIT`. Each sale line identifies one existing catalog item and uses its catalog unit exactly. A presentation is an existing distinct `InventoryItem`; names, lot strings, and fragrance metadata do not establish identity. No automatic conversion between `MILLILITER`, `LITER`, `UNIT`, or other units is implied.

### Prices and totals

The operator enters each draft line's unit price; v0.8.0 has no price list or automatic pricing. Prices and totals are BRL. The backend stores exact decimals, rounds each extended line amount to two decimal places using `HALF_UP`, and sums those rounded line amounts for the authoritative order total. Angular may show a clearly provisional preview, but only the backend response is authoritative.

### History and corrections

Confirmed line snapshots preserve the catalog item identifier, display name, unit/presentation, quantity, unit price, and rounded line amount. The order/sale preserves its customer identifier and contact snapshot. Each sale line records every batch and exact quantity allocated by inventory, plus the corresponding movement identifier. Inventory movements carry an opaque sale and sale-line reference. Batch identity and genealogy come from catalog IDs, batch IDs, and movement references, never from display names or lot-code parsing.

Cancelling an unconfirmed draft has no stock effect. A confirmed sale is not cancelled or rewritten. A physical return or stock correction is a separate operator-authorized, audited inventory correction; any quantity returned to stock must be physically present, inspected, accepted for stock, assigned to its original sold batch, and pass inventory eligibility including expiration. It creates a new movement and retains the sale-line reference. A sale cancellation alone never restores stock. Confirmed commercial values are immutable; v0.8.0 does not include an edit, refund, or full returns-management workflow.

## Operator experience and platform

Authenticated operators use Angular screens for customer maintenance/search, draft order creation/editing, confirmation, and sale history. All operator-facing copy and errors are pt-BR. Spring Boot validates requests, calculates totals, confirms state, and owns all stock decisions. Existing same-origin Spring Security sessions, authorization, CSRF protection, error format, and `/api/v1` conventions remain in effect.

PostgreSQL remains the source of truth and Flyway owns schema evolution. Persistence stays relational and compact, with bounded contact and snapshot fields, paginated list/history queries, and indexes only for customer search, order lookup/status/date filters, and sale-line allocation joins. No document or attachment storage is required.

## Outside v0.8.0

- Fiscal issuance or legal-compliance claims, SEFAZ integration, certificates, XML/PDF generation or storage;
- Payment gateways, payment reconciliation, refunds, credit, accounts receivable, and payment records;
- Reservations, inventory holds, and draft-time stock changes;
- Price lists, discounts, taxes, margins, costing, and automated pricing;
- CRM profiling, government identifiers, addresses, marketing consent, or customer segmentation;
- E-commerce, purchasing automation, multitenancy, SaaS onboarding, or public hosting;
- Attachments, images, blobs, and provider Storage SDKs;
- Changes to existing V1 inventory, production, genealogy, authentication, or HTTP behavior.

Fiscal feasibility may be evaluated separately after the required commercial workflow ships. It is optional and is not a v0.8.0 release dependency.
