# Issue #138 — Validate V1 operational readiness

## Objective

Perform the final V1 acceptance pass against the integrated `develop` state and prove that Céu de Lavanda can operate inventory and internal production without using the legacy spreadsheet as an operational source of truth.

This is a release-readiness validation issue. It must add confidence through integration/acceptance coverage and evidence; it must not hide new product functionality or silently repair blockers.

## Source of truth

Apply, in order:

1. GitHub issue #138;
2. `AGENTS.md`;
3. `backend/AGENTS.md`;
4. `frontend/AGENTS.md`;
5. `docs/product/scope-v1.md`;
6. `docs/specs/0134-define-initial-inventory-migration-mapping.md`;
7. `docs/specs/0136-import-initial-inventory-snapshot.md`;
8. the implemented production contracts from #146 through #154;
9. existing backend/frontend release-level workflow tests from #98 and #117;
10. this specification for the concrete acceptance strategy of #138.

If any blocker is discovered, do not fix it silently inside #138. Record it clearly and create a separate scoped issue before release preparation.

## Validation strategy

Use three complementary evidence levels:

1. **Automated backend acceptance** with PostgreSQL/Testcontainers and real application/module contracts.
2. **Automated frontend acceptance** with the real Angular router/components and `HttpTestingController` only at the network boundary.
3. **Real-source migration acceptance** using the actual external Céu de Lavanda CSV against a disposable database, never committing the CSV or operational data.

Do not add Playwright, Cypress, Selenium, or another browser E2E framework solely for this issue.

## Existing coverage to preserve

Do not replace or weaken existing focused tests.

Important existing release-level/focused coverage includes:

- `OperationalInventoryWorkflowIntegrationTest` for catalog -> receipt -> FEFO -> balances/history;
- initial snapshot parser/import PostgreSQL coverage from #136;
- dashboard backend/frontend coverage from #135/#137;
- `RegisterProductionIntegrationTest` and rollback/allocation coverage from #150;
- `GetBatchGenealogyIntegrationTest` from #151;
- `app.operational-workflow.spec.ts` for the v0.4 inventory operator flow;
- focused production setup, registration, and genealogy frontend tests from #152/#153/#154.

#138 should prove cross-capability composition that these focused tests do not already establish. Avoid copying every focused assertion into one huge scenario.

## Backend V1 acceptance scenario

Add one repository-level PostgreSQL/Testcontainers acceptance scenario, or the smallest equivalent composition, that proves the integrated backend chain.

A dedicated test-only package such as `com.ceudelavanda.lavandaflow.acceptance` is allowed. This does not create a Spring Modulith module or a new production architecture boundary.

Use a fixed application `Clock` and derive all date-sensitive fixture values from that fixed business date.

### Phase 1 — Initial snapshot

Use a **synthetic** CSV fixture matching the approved #134 source shape. Never copy the real operational CSV into tests.

The automated scenario must prove:

- `DRY_RUN` produces the deterministic classification with zero database writes;
- `APPLY` creates active catalog items according to #134;
- positive rows create opening batches and one auditable `ENTRY` each;
- zero-stock rows create catalog only;
- imported balances and batch/current-stock reads agree;
- PostgreSQL is sufficient after apply; no subsequent test step reads the CSV again.

Include source data useful for later acceptance steps, such as at least one available positive-stock ingredient and one zero-stock item. Expiration dates must be deterministic relative to the fixed business date.

### Phase 2 — Existing inventory operations

On the imported state:

- inspect representative imported item balance, batches, expiration, and movement history;
- register one additional stock receipt through the existing application contract;
- execute a FEFO withdrawal through the existing backend-authoritative use case;
- assert persisted per-batch reductions, final total stock, and immutable movement history;
- assert no negative balance.

Do not duplicate FEFO selection logic in the test. Assert the persisted/result contract returned by the backend.

### Phase 3 — Dashboard and alerts

Configure only the minimum state required to make the dashboard/alerts meaningful.

Validate that:

- the dashboard summary is obtained through the existing backend dashboard use case/contract;
- observed counts are coherent with the deliberately constructed acceptance state;
- low-stock and expiration alerts remain coherent after receipt/withdrawal/production changes;
- expiration semantics follow the fixed application `Clock`, including `expiresAt <= today`.

Do not implement dashboard or alert counting logic in acceptance helpers.

### Phase 4 — Production setup

After the initial import has completed, create only the additional catalog items needed for production setup through normal catalog application/public contracts.

The imported CSV intentionally does not fabricate production metadata. Do not retrofit migration behavior merely to make the production acceptance easier.

Create the minimum production chain:

```text
external/imported source batch
        -> production A
        -> internally produced intermediate batch
        -> production B
        -> internally produced final batch
```

Requirements:

- configure stable production reference metadata through existing catalog behavior;
- create a minimum formula for the intermediate output;
- create a minimum formula for the final output;
- formulas identify item requirements, not concrete batch IDs;
- use exact `BigDecimal` values and no automatic unit conversion.

### Phase 5 — First production

Register production A with exact source-batch allocations.

Use backend-generated lot mode.

Assert through authoritative persisted/read contracts that:

- exact source batch quantity is reduced by the exact requested decimal amount;
- corresponding consumption movement is immutable/auditable;
- exactly one distinct output inventory batch is created;
- exactly one stock-creating movement exists for the output batch;
- the definitive lot follows `TTT-EEE-LLL-MM-YYYY`;
- `LLL` is backend-confirmed, not predicted by the test/frontend;
- production execution, consumption records, lot allocation, inventory effects, and output relationship are all committed together;
- no balance becomes negative.

Do not re-test every concurrency/rollback branch already covered by #148/#149/#150. The release-level scenario proves successful composition; existing focused rollback tests remain authoritative for failure paths.

### Phase 6 — Intermediate consumption and second production

Register production B using the output batch from production A as an exact source batch.

Assert:

- the internally produced intermediate batch is eligible as a normal inventory input when backend rules allow it;
- its exact balance decreases according to production B consumption;
- one new distinct final output batch is created;
- the second definitive generated lot is backend-provided;
- all movement/history state remains auditable and non-negative.

### Phase 7 — Recursive genealogy

Using stable batch identities returned by the committed productions:

- query upstream from the final output batch and prove traversal reaches production B, the intermediate batch, production A, and the original external/imported source batch;
- query downstream from the original source batch and prove traversal reaches the intermediate output and final output across at least two production levels;
- assert exact persisted consumed quantities on relevant edges;
- lot codes are display values only;
- do not infer edges from lot-code strings in the test.

Use the existing genealogy application/API model rather than inspecting production tables directly for graph assertions unless a persistence assertion is specifically needed to prove atomic state.

## Frontend V1 acceptance scenario

Keep the existing `app.operational-workflow.spec.ts` inventory scenario intact unless a minimal adjustment is required by integrated behavior.

Add a separate high-value V1 production/readiness integration spec rather than turning the existing inventory workflow test into one monolithic file.

Suggested shape:

```text
frontend/src/app/app.v1-operational-readiness.spec.ts
```

Use:

- real application routes;
- real feature components;
- real typed frontend API services;
- `HttpTestingController` only at the network boundary.

Do not mock feature services for the principal flow.

### Frontend scenario must prove

At minimum compose these operator-facing behaviors:

1. start from the application shell/dashboard and render backend summary data;
2. navigate to production setup;
3. inspect/create the minimum formula state required by the flow using existing UI contracts;
4. open internal production registration;
5. select/use an existing formula;
6. enter exact source-batch allocations, including the backend-provided batch identity;
7. use generated lot mode without any frontend-authored definitive `LLL` sequence;
8. submit and show success only after backend confirmation;
9. display the definitive returned internal lot and output batch identity;
10. navigate to genealogy for the output batch;
11. render recursive upstream/downstream data exactly as returned by the backend;
12. verify principal operator-facing labels/actions are pt-BR;
13. verify no stale optimistic stock/production success is presented before backend confirmation.

Do not reconstruct formula scaling, stock eligibility, FEFO, expiration, lot sequencing, or genealogy in frontend fixtures. Fixtures represent backend responses only.

Existing focused tests remain responsible for detailed form-validation/error branches. The acceptance spec should stay focused on cross-route composition and backend-confirmed state transitions.

## Real operational CSV acceptance

The real Céu de Lavanda CSV remains external to the repository.

### Safety rules

- Never commit or copy the real CSV into `src/test`, `docs`, resources, artifacts, logs, screenshots, or PR attachments.
- Never run `APPLY` against an existing developer/operational database as part of automated acceptance.
- Real-source `APPLY` must use a fresh disposable PostgreSQL database/container dedicated to acceptance.
- Always run `DRY_RUN` first using the exact same file and explicit effective date.
- Inspect the dry-run report and continue only with zero rejected rows.
- Do not backdate the effective date automatically.
- If the source has become stale relative to the chosen effective date, stop and report the blocker; do not alter import rules.

### Execution evidence

If the real CSV path is available in the Codex/local environment, execute and record:

1. dry-run aggregate result;
2. confirmation of zero database writes after dry-run;
3. apply result against the fresh disposable database;
4. catalog item count;
5. opening-stock batch/movement counts;
6. zero-stock catalog-only behavior;
7. representative normalized duplicate/reference behavior required by #134;
8. representative current-stock/batch/history consistency;
9. confirmation that normal subsequent application reads use PostgreSQL only.

Do not commit a row-by-row dump of operational inventory data. The report should contain aggregate evidence and only the minimum representative facts already approved/documented by #134.

If the real CSV is **not** available in the Codex environment, do not fabricate or claim this criterion passed. Mark the real-source acceptance as pending in the final report and provide the exact safe command/procedure the operator must run next.

A skipped/missing real-source run means #138 is not yet fully ready to merge until that evidence is completed.

## Acceptance report

Create:

```text
docs/operations/v1-operational-readiness.md
```

Keep it concise and factual.

Record:

- tested `develop`/branch commit;
- automated backend acceptance scenario and outcome;
- automated frontend acceptance scenario and outcome;
- real CSV dry-run/apply evidence, or clearly `PENDING` if not executed;
- `./mvnw verify` result;
- `pnpm lint`, `pnpm test`, `pnpm build` results;
- Spring Modulith result;
- known non-blocking warnings;
- blockers, if any;
- remaining operational risks, if any;
- final readiness conclusion: `READY` or `NOT READY`.

Do not mark the report `READY` while a required acceptance criterion is pending.

## Blocker handling

If acceptance exposes a real defect or missing V1 behavior:

1. do not patch production code silently in #138;
2. document the failing acceptance criterion;
3. capture reproducible evidence;
4. create a separate scoped GitHub issue with the correct area/type/priority/milestone;
5. leave #138 `NOT READY` until the blocker is resolved and the scenario is rerun.

Small test-only fixtures/helpers and documentation changes are allowed in #138. Production behavior changes are not expected.

## Scope guardrails

Do not introduce:

- new V1 features;
- browser E2E frameworks;
- new runtime dependencies;
- test-only production hooks;
- generic CSV import/synchronization;
- performance/load testing;
- deployment/provider work;
- costs, sales, purchasing, planning, fiscal, QR/barcode, or other ERP expansion;
- frontend-authored lot sequence allocation;
- frontend genealogy reconstruction.

Do not modify backend/frontend production code unless a change is strictly test infrastructure with zero runtime behavior. Any actual product defect belongs in a separate issue.

## Validation gates

Backend, from `backend/`:

```bash
./mvnw verify
```

Frontend, from `frontend/`:

```bash
pnpm lint
pnpm test
pnpm build
```

Do not force `pnpm test -- --run`.

Then review:

```bash
git diff --check
git status --short
```

Also inspect the complete diff to verify that #138 contains acceptance tests/evidence only and no hidden product implementation.

## Acceptance criteria mapping

#138 is complete only when:

- automated backend acceptance proves import -> inventory operations -> dashboard/alerts -> two-level production -> recursive genealogy composition;
- automated frontend acceptance proves the principal operator production/readiness route composition through backend-confirmed responses;
- the real external CSV has completed dry-run before apply and successful apply against a disposable database, with evidence recorded;
- no normal runtime behavior uses the CSV afterward;
- all principal operator copy sampled by the integrated workflows is pt-BR;
- all stock/production quantities remain exact and non-negative;
- full backend/frontend validation gates pass;
- known warnings are recorded;
- no unresolved blocker remains;
- the acceptance report concludes `READY`.

## Out of scope

Everything listed as out of scope in GitHub issue #138 remains out of scope here. This specification does not authorize product changes.