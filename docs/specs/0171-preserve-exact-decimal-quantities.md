# Issue #171 — Preserve exact decimal quantities across HTTP boundaries

## Objective

Correct the v0.5.0 release-blocking HTTP/frontend precision defect so every inventory and production quantity supported by the backend `BigDecimal` / PostgreSQL `NUMERIC(19,6)` contract round-trips through Angular without silent IEEE-754 mutation.

The fix must preserve the existing backend domain, persistence, inventory, production, FEFO, expiration, lot, genealogy, and transactional semantics. It is a transport-boundary correction, not a new quantity model or a domain redesign.

## Source of truth

Apply, in order:

1. GitHub issue #171;
2. `AGENTS.md`;
3. `backend/AGENTS.md`;
4. `frontend/AGENTS.md`;
5. `docs/specs/0139-prepare-v0-5-0-release.md`;
6. current API documentation and OpenAPI contracts;
7. current backend HTTP DTOs and controller tests;
8. current frontend DTOs, services, forms, display helpers, and tests;
9. this specification for the implementation details of #171.

Issue #171 remains authoritative for intent, scope, acceptance criteria, constraints, and out-of-scope behavior. This specification records the approved transport decision and the concrete implementation/test boundaries.

## Release context

Issue #139 discovered the blocker while auditing `release/v0.5.0`.

The fix branch is:

```text
fix/171/preserve-exact-decimal-quantities
```

It is intentionally based on `release/v0.5.0`, because #171 is a release-blocking stabilization defect discovered after the release branch was cut.

The completed fix must return to `release/v0.5.0` through a focused PR. Do not target `develop` directly from this branch.

Do not change release version metadata in #171.

## Defect

JavaScript `Number` cannot exactly represent the complete approved decimal quantity space.

For example:

```text
Number("9999999999999.123456")
=> 9999999999999.123
```

and a numeric JSON response has the same problem during browser parsing:

```text
JSON.parse('{"quantity":9999999999999.123456}').quantity
=> 9999999999999.123
```

Therefore the current transport can change a valid business quantity before the backend receives it and can change an exact backend value before Angular displays or reuses it.

The backend domain and database remain exact; the defect is at the HTTP/browser boundary.

## Approved decision

Use a canonical exact-decimal string representation at the HTTP boundary for backend `BigDecimal` quantity values.

```text
Backend domain/application/persistence
BigDecimal / NUMERIC(19,6)
        ↓
HTTP JSON
plain decimal string
        ↓
Angular DTO/form/display
string
```

Example:

```json
{
  "quantity": "9999999999999.123456"
}
```

### Backend response rule

Every relevant HTTP `BigDecimal` response value must serialize as a JSON string using plain decimal notation equivalent to `BigDecimal.toPlainString()`.

Do not emit scientific notation for exact decimal quantities.

### Backend request rule

Canonical v0.5.0 Angular requests send exact decimal strings.

The backend must continue binding quoted decimal strings exactly to `BigDecimal`.

Representative legacy numeric request payloads must remain accepted where Jackson already supports them, preserving request compatibility for existing clients.

### Frontend rule

Exact inventory/production quantities remain strings throughout Angular transport, local DTO state, form submission, backend-response reuse, and rendering.

Do not convert exact quantities through:

- `Number(...)`;
- `parseFloat(...)`;
- unary numeric conversion;
- arithmetic coercion;
- `DecimalPipe` or another formatter that first coerces the value through JavaScript `number`.

Integer-only controls that are not stock/production quantities, such as expiration-window days, are not part of this rule.

## Compatibility decision

Existing v0.4.0 quantity responses use JSON numeric values. Canonical v0.5.0 responses intentionally change exact `BigDecimal` fields from JSON numbers to JSON strings.

This is an explicitly approved corrective API contract change because preserving the old response representation permits silent quantity corruption.

Requirements:

- routes remain unchanged;
- field names remain unchanged;
- wire enum/string values remain unchanged;
- IDs remain unchanged;
- numeric request payloads remain accepted where currently supported;
- canonical v0.5.0 Angular requests use strings;
- canonical v0.5.0 responses use strings;
- OpenAPI and API/release documentation explicitly describe the corrected representation;
- do not claim byte-compatible v0.4 response payloads;
- do not add duplicate sibling fields such as `quantityText` merely to preserve the defective representation.

Backend and frontend are released together as one Lavanda Flow product version.

## Architectural decision

No ADR is required for this correction.

The change is a concrete HTTP representation fix within the existing architecture. It does not introduce a new bounded context, persistence strategy, distributed-system model, client architecture, media type, or API versioning scheme.

An ADR would only become necessary if implementation requires a broader durable decision such as a parallel API version/media type or permanent dual-representation strategy. Those approaches are not approved by #171.

## Scope

### Backend

Implement the smallest centralized HTTP serialization solution that makes relevant `BigDecimal` response values plain decimal JSON strings.

Expected responsibilities:

- centralize `BigDecimal` response serialization rather than annotating every DTO independently;
- use plain-string semantics;
- preserve standard `BigDecimal` request deserialization;
- preserve representative numeric request compatibility;
- update OpenAPI `BigDecimal` representation to string;
- add focused JSON/OpenAPI contract coverage;
- update representative inventory and production controller tests to assert the wire contract.

The expected configuration area is `shared/config`. Do not move business rules into `shared`.

Do not modify:

- domain quantity types;
- application command/result quantity types;
- controller business behavior;
- JPA entities solely for serialization;
- PostgreSQL quantity precision;
- Flyway schema;
- stock/production invariants.

### Frontend

Correct all quantity-bearing API boundaries, not only the production screens that exposed the defect during the audit.

At minimum audit and update:

1. stock receipt;
2. FEFO withdrawal;
3. minimum-stock configuration;
4. inventory overview/current quantities;
5. inventory batch quantities;
6. movement history;
7. inventory alerts;
8. production formula create/update/read;
9. production registration requests;
10. production execution/consumption responses;
11. backend-confirmed inventory refresh after production;
12. recursive genealogy consumed quantities.

Also audit any currently unconsumed public frontend DTOs or service contracts touched by the same exact-quantity model so new consumers do not inherit an inconsistent type.

For exact quantity properties:

- use `string` in transport DTOs;
- keep Reactive Form input values as strings;
- send validated normalized decimal text directly;
- reuse exact backend response strings directly when repopulating forms;
- render losslessly with the existing decimal-string formatting facility where appropriate;
- preserve pt-BR presentation;
- do not introduce client-side quantity arithmetic.

## Exact-decimal frontend validation

Frontend validation remains input-shape validation only. The backend remains authoritative for business rules and persistence limits.

The UI may validate the existing syntactic contract before submit:

- positive decimal input where the existing workflow requires positive quantities;
- at most six fractional digits;
- existing maximum integer-digit rule where already part of the backend/persistence contract;
- no exponent notation when the current UI does not support it.

Do not use `Number(value) > 0` to validate positivity.

Use lexical/string-safe validation instead.

A valid decimal text consisting only of zeros represents zero; a positive validator can reject it without numeric conversion.

Do not introduce a narrower IEEE-754-safe range.

Do not duplicate stock availability, FEFO eligibility, expiration, formula scaling, generated-lot sequence, or genealogy rules in Angular.

## Decimal normalization

Preserve the exact numeric value, not necessarily the user's cosmetic spelling, unless the existing workflow explicitly preserves that spelling.

Examples of equal values:

```text
50
50.0
50.000000
0001.230000
```

Canonical request handling may trim surrounding whitespace already rejected/normalized by current forms, but must not convert the value through `number`.

Do not invent a frontend decimal arithmetic abstraction.

For backend response serialization, use the `BigDecimal` value's plain string representation. Do not use engineering/scientific notation.

## Known affected frontend files

The implementation must confirm the final list from the current branch, but known affected transport DTOs include:

- `frontend/src/app/features/inventory/data-access/fefo-withdrawal.dto.ts`;
- `frontend/src/app/features/inventory/data-access/inventory-operations.dto.ts`;
- `frontend/src/app/features/inventory/data-access/inventory-alert.dto.ts`;
- `frontend/src/app/features/receipts/data-access/stock-receipt.dto.ts`;
- `frontend/src/app/features/production/data-access/production-formula.dto.ts`;
- `frontend/src/app/features/production/data-access/production-execution.dto.ts`;
- `frontend/src/app/features/production/data-access/production-genealogy.dto.ts`.

Known affected form/workflow code includes:

- stock receipt page;
- FEFO withdrawal panel;
- inventory operational/minimum-stock page;
- production formula form page;
- production registration page.

Known affected display code includes quantity displays in:

- receipts;
- FEFO confirmation/result;
- inventory overview/batches/history/alerts;
- formulas;
- production registration/result/refresh;
- genealogy.

The implementation must search the entire frontend rather than treating this list as exhaustive.

## Backend implementation boundary

Prefer one centralized HTTP/Jackson configuration for `BigDecimal` response serialization.

The implementation must prove that this does not accidentally change non-HTTP persistence/domain behavior.

Do not create per-feature serializers unless central configuration is proven unsafe.

Do not introduce a third-party decimal or JSON library.

OpenAPI must describe canonical exact decimal values as strings so generated/documented clients do not infer JavaScript numeric safety.

A representative schema may document the format/pattern/example if useful, but do not invent a wire enum or wrapper object for decimals.

## API surface audit

Before finishing implementation, search all backend HTTP request/response DTOs for `BigDecimal` and classify every occurrence as:

- exact quantity covered by the canonical decimal-string contract;
- non-quantity decimal that should still use the same global exact representation;
- internal/non-HTTP type unaffected by serialization.

Because the approved solution is centralized, the final behavior must be intentional for every HTTP `BigDecimal` field, not an accidental partial feature subset.

Do the corresponding frontend search for every quantity-bearing `number` DTO field and every numeric coercion.

## Required regression values

Tests must include exact values that currently expose or guard the defect.

At minimum:

```text
0.000001
50
100.5
100.000001
8589934591.999999
8589934592.000001
9999999999999.123456
9999999999999.999999
```

Also verify a text form such as:

```text
0001.230000
```

is transmitted without conversion through JavaScript `Number` if it passes the current form normalization policy.

A value with seven fractional digits, for example:

```text
1.1234567
```

must remain rejected according to the existing quantity precision contract.

Do not choose test values that exceed the actual backend `NUMERIC(19,6)` precision/range.

## Backend tests

Add or update coverage proving at least:

1. quoted decimal request values bind exactly to `BigDecimal`;
2. a representative legacy numeric request still binds successfully;
3. `9999999999999.123456` is not mutated during binding;
4. representative inventory `BigDecimal` responses are JSON strings;
5. representative nested inventory response quantities are strings;
6. production formula quantities are strings;
7. production execution output/consumption quantities are strings;
8. genealogy consumed quantities are strings;
9. plain decimal notation is used, not scientific notation;
10. `/v3/api-docs` describes the canonical decimal representation as string;
11. existing validation and business error behavior remains unchanged.

Prefer focused web/contract tests plus existing controller tests. Do not duplicate all domain/integration behavior solely for serialization.

## Frontend tests

Add or update tests proving at least:

1. stock receipt request sends exact decimal text;
2. FEFO withdrawal request sends exact decimal text;
3. minimum-stock request sends exact decimal text;
4. formula output/ingredient requests send exact decimal text;
5. production output/source-allocation requests send exact decimal text;
6. no affected request builder calls `Number`/`parseFloat` for quantity conversion;
7. response fixtures use decimal strings;
8. exact backend values render without numeric coercion;
9. formula edit repopulation preserves the exact response value;
10. backend-confirmed production output/consumption quantities remain exact;
11. inventory refresh values remain exact;
12. genealogy consumed quantities remain exact;
13. seven fractional digits remain rejected;
14. normal small values continue to work;
15. boundary values around JavaScript precision loss remain unchanged.

Tests should fail against the current defective implementation for at least one release-blocking value.

## Existing formatter reuse

Prefer the existing frontend exact-decimal string formatter/helper under:

```text
frontend/src/app/core/i18n/decimal-string.ts
```

when it satisfies the display requirement.

Extend it only when a concrete display need is missing and the change remains generic formatting rather than business logic.

Do not add `Decimal.js`, `Big.js`, or another arbitrary-precision arithmetic dependency.

## Domain invariants that must not change

The implementation must preserve:

- backend quantities use `BigDecimal`;
- PostgreSQL quantity columns use the approved `NUMERIC(19,6)` model;
- stock never becomes negative;
- every stock change creates immutable auditable movement history;
- corrections create new movements rather than rewriting history;
- balance-changing operations remain transactional;
- FEFO remains backend-authoritative;
- availability/expiration remain backend-authoritative;
- `expiresAt <= today` remains expired;
- date-dependent behavior uses the application `Clock`;
- generated lot allocation remains backend-authoritative;
- one production execution creates exactly one output batch;
- production remains atomic;
- genealogy remains based on persisted stable relationships, never lot-code parsing;
- module boundaries from ADR 0009 remain unchanged.

## Module and layering constraints

No new module is needed.

Expected ownership:

- HTTP serialization/OpenAPI configuration: existing cross-cutting backend configuration area;
- backend DTO/domain values remain owned by their current modules;
- frontend transport types remain under feature data-access;
- frontend formatting remains under existing i18n/core helpers;
- UI components/pages remain presentation/input orchestration only.

Do not move business quantity rules into shared configuration or frontend helpers.

## Documentation

Update the current API documentation to record:

- canonical exact decimal string responses in v0.5.0;
- canonical string requests from the Angular client;
- continued acceptance of representative numeric request values where applicable;
- intentional v0.4 numeric-response -> v0.5 string-response correction;
- reason: exact `BigDecimal` / `NUMERIC(19,6)` preservation across browser JSON boundaries.

Do not perform unrelated documentation cleanup from #139 in this issue.

The final v0.5.0 release notes will reference the corrective wire-contract change separately during #139.

## Explicitly rejected alternatives

Do not implement these unless the approved approach is proven impossible and the issue is revisited:

### Narrow JavaScript-safe quantity range

Rejected because it creates a frontend-only constraint smaller than the approved backend range and still cannot safely represent arbitrary backend response values.

### Lossless custom JSON parser / raw-text HttpClient path

Rejected because it adds disproportionate parsing/interceptor complexity and does not simplify request serialization.

### Decimal arithmetic dependency

Rejected because this issue needs lossless transport/display, not frontend arithmetic.

### Dual fields such as `quantity` + `quantityText`

Rejected because it permanently duplicates the public quantity contract and preserves a misleading lossy field.

### New API version/media type

Out of scope for this release-blocking correction.

## Out of scope

- database schema or precision changes;
- Flyway migrations;
- new quantity/domain rules;
- automatic unit conversion;
- generic frontend decimal arithmetic;
- API v2 or media-type redesign;
- authentication/deployment work;
- framework/dependency upgrades;
- bundle-size optimization;
- unrelated flaky-test cleanup;
- #139 cutover-guide corrections;
- #139 stale architecture/product documentation corrections;
- #139 operator-visible localization findings not directly changed by #171;
- release version bump/removal of `-SNAPSHOT`.

## Implementation sequence

Use the smallest complete vertical sequence:

1. re-read issue #171 and this spec;
2. inventory current backend HTTP `BigDecimal` and frontend exact-quantity boundaries;
3. add focused failing regression tests for the precision defect;
4. implement centralized backend response serialization;
5. configure/verify OpenAPI representation;
6. convert frontend exact-quantity DTOs from `number` to `string`;
7. remove quantity `Number`/numeric coercions from form-to-request paths;
8. replace lossy exact-quantity display formatting;
9. update all affected frontend fixtures/tests;
10. update focused API documentation;
11. run full backend/frontend gates;
12. search again for residual exact-quantity numeric coercion;
13. review the complete diff for unrelated changes.

Do not introduce speculative abstractions while consolidating repeated changes. A small helper is justified only when actual repeated behavior already exists and the helper has one clear transport/formatting responsibility.

## Validation

### Backend

From `backend/`:

```bash
./mvnw verify
```

Required:

- all tests green;
- Spring Modulith verification green;
- PostgreSQL/Testcontainers tests green;
- OpenAPI contract tests green;
- no Flyway migration added;
- no domain/persistence precision change.

### Frontend

From `frontend/`:

```bash
pnpm lint
pnpm test
pnpm build
```

Do not force `pnpm test -- --run`.

Known unrelated bundle warnings may remain non-blocking if unchanged.

### Repository

From the repository root:

```bash
git diff --check
git diff
git status --short
```

Review the entire diff.

## Acceptance assessment

#171 is complete only when all issue acceptance criteria are satisfied and the release-blocking reproduction can no longer mutate the value:

```text
9999999999999.123456
```

The value must remain exact through:

```text
Angular form/input
-> request body
-> Jackson BigDecimal binding
-> backend response serialization
-> browser response handling
-> Angular reuse/display
```

No quantity in that path may pass through JavaScript `number`.

## Commit guidance

Do not commit until the implementation report and diff have been reviewed.

When commits are later requested, use coherent checkpoints with Conventional Commits and `(#171)`.

Possible checkpoints, depending on the actual final diff:

```text
fix(api): serialize exact decimal quantities as strings (#171)
fix(frontend): preserve exact quantity transport (#171)
test(api): cover exact decimal HTTP contracts (#171)
docs(api): document exact decimal wire format (#171)
```

Do not create artificial commits just to match this example.

## Final implementation report

Before any commit/push/PR, report:

1. files created/modified;
2. final backend `BigDecimal` HTTP surface audited;
3. final frontend exact-quantity surface audited;
4. serialization/OpenAPI implementation decision;
5. compatibility behavior for numeric and quoted requests;
6. response wire contract evidence;
7. frontend request precision evidence;
8. frontend response/display precision evidence;
9. boundary regression values tested;
10. tests added/updated;
11. production code changed and reason;
12. confirmation of no Flyway/schema/domain-rule changes;
13. `./mvnw verify` result and test count;
14. Spring Modulith result;
15. `pnpm lint` result;
16. `pnpm test` result and test count;
17. `pnpm build` result and warnings;
18. residual quantity `Number`/`parseFloat` search result;
19. API documentation changes;
20. acceptance criteria still unmet, if any;
21. `git diff --check` result;
22. complete `git status --short`.

Do not commit.
Do not push.
Do not open a PR.
