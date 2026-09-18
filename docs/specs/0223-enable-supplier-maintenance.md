# Issue #223 — Enable supplier maintenance

## Status

Implementation specification for issue #223.

## Source of truth

GitHub issue #223 remains the source of truth for product intent, scope, acceptance criteria, constraints, and out-of-scope work.

The implementation must also follow:

- AGENTS.md;
- backend/AGENTS.md;
- frontend/AGENTS.md;
- docs/architecture/architecture.md and docs/architecture/backend-structure.md;
- docs/architecture/data-model.md;
- docs/architecture/decisions/0007-standardize-api-error-contract.md;
- the current supplier, inventory receipt, security, OpenAPI, and Angular supplier implementations.

If a detail is not specified here, preserve current behavior and implement the smallest complete change that satisfies #223.

## Branch

feature/223/enable-supplier-maintenance

The branch starts from develop commit 4137936d7ad3ce4eb0c7d455a0875651179fdb2b, immediately after #222.

## Objective

Allow an authenticated operator to safely maintain an existing supplier from the Angular supplier workspace without database access, destructive deletion, supplier identity replacement, or expansion into CRM/purchasing scope.

## Current baseline

The current implementation already provides:

- POST /api/v1/suppliers for registration;
- GET /api/v1/suppliers/{supplierId} for detail;
- GET /api/v1/suppliers for paginated name/active search;
- Supplier domain methods rename, changeIdentifier, changeContact, changeNotes, activate, and deactivate;
- SupplierRepository with semantic save/findById operations;
- SupplierLookup as the public suppliers-module contract consumed by inventory;
- SupplierSnapshot containing id, name, and active;
- RegisterStockReceipt validation that rejects a supplied inactive supplier through SupplierLookup;
- Angular supplier list, registration, and stable detail routes;
- explicit active/inactive filtering in the supplier list;
- persisted inventory_batch.supplier_id references to the supplier UUID.

Issue #223 must expose the already-supported maintenance semantics rather than redesigning suppliers.

## Resolved scope decisions

### Supported mutable fields

The supplier maintenance contract supports exactly:

- name;
- optional identifier;
- optional contact;
- optional notes;
- active.

The supplier UUID is not editable and must remain unchanged.

No hard-delete capability is introduced.

### Supplier identity and history

Maintenance updates the existing supplier row and preserves the same UUID.

Existing inventory batches and receipt history that reference supplier_id must continue referencing that same supplier identity. Updating display/contact metadata or active state must not rewrite, duplicate, detach, or delete historical inventory data.

Deactivation is the supported mechanism for preventing future use. Historical supplier detail remains readable.

### Active supplier semantics

The suppliers module remains the owner of supplier active state.

Inventory must continue consuming only the existing public SupplierLookup / SupplierSnapshot contract. Do not import suppliers infrastructure into inventory.

RegisterStockReceipt already rejects an inactive supplier. #223 must preserve that behavior without duplicating or moving the rule.

After maintenance deactivates a supplier:

- supplier detail remains available;
- supplier search can still find it, including active=false filtering;
- SupplierLookup returns the same supplier identity with active=false;
- new stock receipts using that supplier remain rejected by the existing inventory rule.

After reactivation, SupplierLookup must expose active=true again and existing receipt behavior may accept the supplier subject to the normal inventory rules.

Do not add supplier-active logic to Angular beyond presentation and operator affordances.

### Identifier semantics

The current schema stores identifier as nullable VARCHAR(255) with no uniqueness constraint.

#223 must not invent uniqueness, canonicalization, tax-ID validation, CNPJ/CPF semantics, or external identity rules.

identifier remains optional free-form operator metadata under the existing length/normalization behavior.

### No schema migration expected

The current supplier table already contains name, identifier, contact, notes, active, and stable UUID identity. inventory_batch already references supplier(id).

No Flyway migration is expected. Add one only if inspection proves an actual missing persistence requirement for #223. Never modify historical migrations.

## Backend contract

### Application use case

Add one focused suppliers application use case, conceptually:

- UpdateSupplier;
- UpdateSupplierCommand.

The command carries:

- supplierId;
- name;
- identifier;
- contact;
- notes;
- active.

The use case must:

1. load the existing Supplier by UUID through SupplierRepository;
2. use the existing SupplierNotFoundException behavior when absent;
3. call Supplier.rename for name;
4. call changeIdentifier, changeContact, and changeNotes for optional metadata;
5. call activate or deactivate according to requested state;
6. save the same aggregate through SupplierRepository;
7. return SupplierResult;
8. execute transactionally.

Do not recreate the supplier with a new UUID and do not mutate JPA state directly from the application service.

Do not place maintenance rules in SupplierController.

### Domain

Reuse the existing Supplier aggregate behavior.

Required invariants:

- UUID never changes;
- name remains non-blank and normalized by the aggregate;
- blank optional identifier/contact/notes normalize consistently with existing domain behavior;
- active changes only through activate/deactivate;
- no hard delete;
- no CRM, purchasing, pricing, or supplier-history aggregate is introduced.

If a new structured supplier-maintenance exception is genuinely needed to prevent raw domain exceptions from leaking through HTTP, use the existing ADR 0007 pattern and keep it narrowly scoped. Do not invent a generic exception hierarchy.

### HTTP

Add one explicit maintenance endpoint:

PUT /api/v1/suppliers/{supplierId}

Expected request shape:

~~~json
{
  "name": "Fornecedor Lavanda",
  "identifier": "ID-001",
  "contact": "contato@example.com",
  "notes": "Observação opcional",
  "active": true
}
~~~

The request must not contain an editable id.

Validation must preserve existing registration limits:

- name: required, non-blank, max 255;
- identifier: optional, max 255;
- contact: optional, max 255;
- notes: optional with existing database/domain behavior;
- active: required/non-null.

Successful update returns HTTP 200 with the normal SupplierResponse representation.

Document/infer at least:

- 200 success;
- 400 malformed/validation request;
- 404 unknown supplier.

Keep authentication and CSRF behavior consistent with existing secured mutation endpoints.

Do not add PATCH, delete, bulk update, or separate activate/deactivate endpoints unless the existing repository architecture makes the single PUT contract impossible. The preferred contract is one explicit PUT.

### Persistence

Reuse SupplierRepository, JpaSupplierRepository, SupplierMapper, and the existing supplier table.

The update must persist the existing row identity. Do not access inventory persistence from suppliers production code.

No uniqueness pre-query or new database constraint is required.

## Inventory boundary preservation

No inventory production-code change is expected.

The existing RegisterStockReceipt implementation already:

- looks up suppliers through SupplierLookup;
- rejects missing suppliers;
- rejects inactive suppliers;
- never imports suppliers persistence internals.

#223 tests should prove that supplier maintenance preserves this public contract rather than moving the rule.

For history preservation, use supported module APIs or focused integration/database test fixtures where appropriate. Production code must not cross module infrastructure boundaries.

## Frontend contract

### Routing

Extend the supplier feature with a stable edit route:

/suppliers/:supplierId/edit

The edit route must be declared before /suppliers/:supplierId.

Add an explicit accessible edit action from supplier detail.

Do not add inline list editing.

### Form implementation

frontend/AGENTS.md defines Reactive Forms as the default for new forms and says not to introduce Signal Forms unless explicitly adopted.

For #223, the new supplier edit page must use Angular Reactive Forms.

Do not migrate the existing supplier registration page from Signal Forms as part of this issue.

This intentional mixed implementation is scope discipline: #223 adds one new edit form and does not refactor unrelated existing registration code.

### Edit page

Add a focused supplier edit page that:

1. loads the supplier by route UUID through SupplierApiService;
2. exposes loading/error/retry states using existing shared UI;
3. pre-fills name, identifier, contact, notes, and active;
4. allows activation/deactivation through the same form;
5. submits only the supported maintenance fields;
6. prevents accidental duplicate submissions;
7. maps backend validation/not-found/infrastructure failures through the centralized error utilities;
8. preserves entered form values after a failed save;
9. on success navigates to the stable supplier detail route or otherwise reloads authoritative persisted state;
10. keeps all operator-facing copy and accessibility text in pt-BR.

Use Angular Material/CDK and current supplier feature patterns.

Do not perform optimistic supplier state changes before backend success.

### Data access

Extend supplier.dto.ts with a dedicated UpdateSupplierRequest rather than reusing RegisterSupplierRequest if that would blur registration and maintenance semantics.

Expected type:

~~~ts
export interface UpdateSupplierRequest {
  readonly name: string;
  readonly identifier: string | null;
  readonly contact: string | null;
  readonly notes: string | null;
  readonly active: boolean;
}
~~~

Extend SupplierApiService with:

update(supplierId, request) -> PUT /api/v1/suppliers/{supplierId}

Do not add direct HttpClient usage to the page.

### Inactive presentation

The supplier list and detail already surface active state. Preserve this.

The edit flow must make active/inactive state clear and allow reactivation.

Do not hide inactive suppliers from detail or erase them from existing all/inactive search behavior.

## Error behavior

Preserve ADR 0007 and the frontend mapHttpError/localization pattern.

The UI must handle at least:

- field validation errors;
- supplier not found;
- infrastructure/unexpected failure.

If the current centralized supplier not-found message already exists, reuse it.

Do not introduce supplier-specific error infrastructure when existing shared mapping is sufficient.

## Tests

### Backend application/domain

Cover at minimum:

- valid update of name, identifier, contact, and notes;
- optional metadata clearing/blank normalization;
- active -> inactive;
- inactive -> active;
- same UUID before/after update;
- supplier not found;
- repository save invoked with the existing aggregate;
- no hard-delete behavior introduced.

### Persistence/integration

Using PostgreSQL/Testcontainers where appropriate, cover:

- updated metadata survives reload;
- UUID is preserved;
- SupplierLookup reflects updated name and active state;
- inactive supplier remains retrievable;
- historical inventory_batch supplier_id remains attached to the same UUID after supplier maintenance, using a focused supported-module or database-level test where practical;
- no Flyway change is required.

### Inventory regression

Prove or retain coverage that:

- an inactive SupplierSnapshot is rejected by RegisterStockReceipt;
- supplier maintenance does not require inventory to import suppliers internals;
- reactivation is visible through SupplierLookup.

Do not duplicate the entire stock-receipt test suite.

### HTTP/security

Cover:

- authenticated PUT success;
- request validation;
- supplier not found;
- CSRF rejection;
- anonymous rejection;
- exact SupplierResponse representation;
- registration/search/detail compatibility remains intact.

### Frontend

Cover:

- edit route precedence and direct navigation;
- detail-page edit action target;
- load and prefilled form;
- editing all supported fields;
- clearing optional fields;
- activation and deactivation;
- successful PUT payload and navigation;
- duplicate-submit prevention;
- backend field validation rendering;
- not-found/load failure and retry;
- save infrastructure failure while preserving the form;
- inactive-state presentation/reactivation;
- exact API service URL/method/body.

## Existing behavior that must not change

- supplier registration contract;
- supplier search name/active filters and pagination;
- supplier detail contract;
- SupplierLookup public contract shape unless a concrete backward-compatible need is proven;
- RegisterStockReceipt missing/inactive supplier behavior;
- inventory batches and receipt history;
- authentication/CSRF model;
- ADR 0007 error envelope;
- module boundaries.

## Out of scope

Do not implement:

- hard delete;
- CRM/customer-relationship concepts;
- supplier pricing;
- purchase orders;
- purchasing automation;
- commercial analytics;
- tax-document validation or external supplier integration;
- supplier change history/audit log beyond current model;
- editing historical batches or receipts;
- new inventory behavior;
- new dependencies, modules, events, or generic CRUD frameworks.

## Acceptance checklist

- [ ] Existing supplier can be opened in a stable edit flow.
- [ ] Name, identifier, contact, and notes can be maintained.
- [ ] Supplier can be activated and deactivated.
- [ ] UUID remains unchanged.
- [ ] Historical batch/receipt references remain attached to the same supplier UUID.
- [ ] Inactive supplier remains inspectable/searchable.
- [ ] Existing stock receipt flow continues rejecting inactive suppliers through SupplierLookup.
- [ ] No hard delete exists.
- [ ] Backend validation remains authoritative.
- [ ] New HTTP maintenance capability is consumed by Angular.
- [ ] OpenAPI reflects the update endpoint.
- [ ] Spring Modulith/module boundaries remain valid.
- [ ] Backend and frontend tests cover the complete vertical slice.
- [ ] No unrelated scope is introduced.

## Validation

Backend:

~~~bash
cd backend
./mvnw verify
~~~

Frontend:

~~~bash
cd frontend
pnpm lint
pnpm test
pnpm build
~~~

Repository review:

~~~bash
git diff --check
git diff
git status --short
~~~

Known non-blocking warnings must be reported, not fixed outside #223.

## Implementation order

1. Read issue #223, AGENTS files, this spec, supplier implementation/tests, SupplierLookup, and RegisterStockReceipt.
2. Add focused backend UpdateSupplier command/use case and tests.
3. Add PUT /api/v1/suppliers/{supplierId} request/controller contract and HTTP/security tests.
4. Confirm PostgreSQL persistence and identity/history preservation; do not create a migration unless actually required.
5. Extend Angular supplier DTO/service.
6. Add the Reactive Forms edit route/page and detail edit action.
7. Add focused frontend tests.
8. Run full backend/frontend validation.
9. Review complete git diff/status for scope leaks before proposing commits.