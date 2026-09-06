# Issue #139 — Prepare v0.5.0 release

## Objective

Prepare Lavanda Flow v0.5.0 as the V1 operational-completion release and prove that the release candidate is functionally complete, architecturally coherent, operationally safe, and appropriately simple before it reaches `main`.

This release gate validates the integrated system. It is not an opportunity to add new product capabilities, modernize dependencies for their own sake, or refactor working code without a concrete release risk.

## Source of truth

Apply, in order:

1. GitHub issue #139;
2. `AGENTS.md`;
3. `backend/AGENTS.md`;
4. `frontend/AGENTS.md`;
5. `docs/product/scope-v1.md`;
6. ADR 0009 and current architecture/data-model documentation;
7. `docs/development/git-workflow.md` and branch-protection policy;
8. `docs/operations/v1-operational-readiness.md` from #138;
9. the implemented contracts and tests already merged into the validated `develop` candidate;
10. this specification for the concrete v0.5.0 release-audit procedure.

The issue remains authoritative for release intent, scope, acceptance criteria, and release mechanics. This specification makes the final audit deterministic and defines how findings are classified.

## Release candidate provenance

The initial `release/v0.5.0` branch must be cut from the validated `develop` head produced by #138:

```text
b63e37c032531ee7c7b73059f1df93d32244a7eb
```

That commit is the squash merge of:

```text
test(repository): validate V1 operational readiness (#138)
```

The release branch must preserve this ancestry. Any later release-stabilization commit must be explicit, reviewable, and directly justified by this audit.

Before release preparation continues, verify that milestone `v0.5.0` has no open implementation issue other than #139 and that all #139 dependencies are closed and merged into `develop`.

## Release principles

The release audit follows these principles:

- correctness before polish;
- evidence before assumptions;
- preserve approved architecture rather than redesign it;
- prefer the smallest complete fix when a genuine blocker exists;
- do not chase newer libraries/framework versions during stabilization;
- do not add abstractions, modules, events, state-management libraries, graph engines, caches, queues, or infrastructure without a concrete requirement;
- distinguish release blockers from useful post-release improvements;
- never weaken backend-authoritative inventory or production rules for release convenience.

## Finding classification

Every finding must be classified before code is changed.

### `BLOCKER`

A defect that prevents v0.5.0 release because it can cause incorrect business behavior, data loss/corruption, broken migration, incompatible API behavior, violated module boundaries, failed mandatory gates, unsafe public exposure under the intended deployment mode, or an inability to execute the documented cutover safely.

A blocker must not be hidden in #139. Prefer a separate scoped issue when the correction is product/architecture behavior rather than pure release metadata or documentation.

### `RELEASE_STABILIZATION`

A small, low-risk correction directly required to make the release artifact internally consistent, such as version metadata, release documentation, configuration mismatch, or a narrowly scoped release-only defect whose change is clearly safer than deferring it.

No feature or broad refactor belongs here.

### `NON_BLOCKING`

A known warning, maintainability concern, documentation improvement, dependency hygiene opportunity, or performance/UX improvement that does not invalidate the V1 release criteria.

Record it; do not automatically fix it on the release branch.

### `OUT_OF_SCOPE`

Anything outside the approved V1/release scope. Do not implement it in #139.

## Phase 1 — Scope and release-history audit

Verify the candidate contains the complete approved v0.5.0 scope and no unrelated work.

At minimum:

- confirm #134, #135, #136, #137, #138 are closed;
- confirm #146 through #154 are closed;
- confirm blocker #168 is closed and included before #138;
- confirm #140, #142, and #143 architecture/governance prerequisites are closed;
- inspect the commit/PR range from `v0.4.0` to the release candidate;
- identify every issue intentionally included in v0.5.0;
- verify no open milestone implementation issue remains besides #139;
- verify no unreviewed feature branch is implicitly required for V1 success.

Use merged GitHub history as evidence rather than relying on a manually maintained issue list.

Expected conclusion: the release candidate is feature-complete and frozen for stabilization.

## Phase 2 — Version and build metadata

On `release/v0.5.0`:

- change backend Maven version from `0.5.0-SNAPSHOT` to `0.5.0`;
- change frontend package version from `0.5.0-SNAPSHOT` to `0.5.0`;
- keep backend and frontend versions identical;
- ensure no additional version-bearing repository metadata incorrectly remains on the old product version;
- do not update dependency versions merely because newer versions exist.

Verify the repository still uses the approved stack:

### Backend

- Java 25;
- Spring Boot 4.1;
- Spring Modulith 2.1;
- Spring MVC;
- JPA/Hibernate;
- PostgreSQL;
- Flyway;
- Testcontainers;
- Maven.

### Frontend

- Angular 22;
- TypeScript;
- Angular Material/CDK;
- Router;
- Reactive Forms;
- Signals;
- RxJS where justified;
- native `HttpClient`;
- Vitest;
- pnpm.

The release audit checks compatibility and justified usage, not whether a newer ecosystem release exists.

## Phase 3 — Architecture and structure audit

### Backend modular-monolith audit

Verify the implemented module graph still matches ADR 0009 and current agent guidance:

```text
production -> catalog
production -> inventory
inventory  -> catalog
inventory  -> suppliers
catalog    -> shared
inventory  -> shared
production -> shared
suppliers  -> shared
```

The exact graph may omit unused allowed edges, but must not introduce reverse/cyclic dependencies.

Explicitly verify:

- V1 modules remain `catalog`, `inventory`, `production`, `suppliers`, and `shared`;
- no speculative `formulas`, `traceability`, `manufacturing`, or generic workflow module appeared;
- `production` remains cohesive and owns formula lifecycle, execution orchestration, generated lot allocation, consumption records, and genealogy;
- `inventory` still owns `Batch`, balances, movements, FEFO, expiration, eligibility, and stock locking;
- `catalog` still owns stable item/production reference metadata;
- `suppliers` remains focused on supplier data and external batch origin;
- `shared` contains cross-cutting infrastructure only and has not become a business-rule dumping ground;
- cross-module communication uses public APIs, stable IDs, and immutable values;
- no module imports another module's repositories, JPA entities, mappers, or infrastructure internals;
- no direct cross-module JPA object relationships were introduced;
- controllers remain thin and contain no stock, FEFO, lot, formula, or genealogy business rules;
- transaction ownership remains in application use cases.

Run Spring Modulith verification and supplement it with targeted code searches for prohibited cross-module imports so the audit does not rely on only one mechanism.

### Production transaction audit

Confirm the implemented production path still represents one local PostgreSQL transaction:

```text
validate formula/catalog facts
        -> allocate definitive generated lot when requested
        -> validate/protect exact source batches
        -> consume stock + write immutable movements
        -> create exactly one output batch + opening movement
        -> persist execution/consumptions/genealogy
        -> commit all or roll back all
```

Verify failure paths leave no partial production, stock, movement, lot-sequence, or genealogy state.

Do not replace this with events, messaging, sagas, or eventual consistency.

### Frontend structure audit

Verify:

- feature/capability organization remains clear;
- HTTP/data-access concerns remain outside presentation components;
- Reactive Forms remain the default for forms;
- Signals are used for appropriate local/derived state rather than introducing a global store without need;
- RxJS is used only where asynchronous composition warrants it;
- no NgRx, Axios, Tailwind, external forms/router library, graph library, or other duplicate framework capability has entered V1;
- no stock/FEFO/expiration/formula-scaling/generated-lot/genealogy authority is duplicated in Angular;
- successful stock/production state is displayed only after backend confirmation;
- operator-facing UI remains pt-BR and accessibility-critical controls remain usable.

## Phase 4 — Domain-invariant audit

Review production code and representative tests for the invariants that must never regress:

- all backend stock/production quantities use `BigDecimal`, never `double` or `float`;
- PostgreSQL quantity columns preserve approved decimal precision;
- stock cannot become negative;
- every stock-changing operation creates immutable auditable history;
- corrections create new movements rather than rewriting old history;
- stock-changing operations are transactional;
- FEFO remains backend-authoritative;
- available-stock and expiration eligibility remain backend-authoritative;
- `expiresAt <= today` remains expired;
- date-dependent rules use the application `Clock`;
- generated lot allocation remains backend-authoritative;
- genealogy is based on explicit stable relationships, never lot-code parsing;
- one production execution creates exactly one distinct output batch;
- produced intermediate batches remain normal inventory batches eligible for later production when backend rules allow it.

Use existing focused tests as evidence. Do not duplicate every invariant into new release-only tests unless a real coverage gap is demonstrated.

## Phase 5 — Persistence and Flyway audit

Flyway is release-critical.

### Migration inventory

Verify:

- migration ordering is valid and deterministic;
- historical migrations already released in `v0.4.0` were not edited in place;
- v0.5.0 adds only forward migrations required by approved features;
- no operational inventory data is seeded through Flyway;
- Hibernate schema auto-generation remains disabled as an evolution mechanism;
- database constraints/indexes needed for uniqueness, non-recycling, referential integrity, concurrency, and query behavior are present where justified;
- there is no duplicate schema ownership outside Flyway.

The current candidate is expected to contain migrations through V13. Confirm this from the repository rather than hard-coding success from the expectation.

### Fresh-install validation

Provision a brand-new disposable PostgreSQL instance and verify all migrations apply from V1 to the candidate head without manual intervention.

Run the backend against that schema and execute the full backend verification suite.

### Real upgrade-path validation: v0.4.0 -> v0.5.0

This is mandatory.

Use the released `v0.4.0` tag as the source for the previous production schema.

1. provision a disposable PostgreSQL instance;
2. initialize it using the `v0.4.0` backend/Flyway migrations;
3. optionally create small representative v0.4.0 business data through supported application paths when useful to prove preservation;
4. start/apply the v0.5.0 release candidate against the same database;
5. verify Flyway accepts existing checksums and applies only the new v0.5.0 migrations;
6. verify existing representative inventory/supplier/batch/movement data remains intact;
7. verify new catalog-production/formula/lot/production/genealogy schema is usable after upgrade;
8. verify no destructive or uncontrolled schema rewrite occurs.

If any already-released Flyway migration checksum changes, treat it as a release blocker unless a documented and safe Flyway repair strategy is explicitly approved. Do not silently run `repair` to make the test pass.

## Phase 6 — Initial inventory migration and cutover audit

Use #138 as the authoritative real-source acceptance evidence; do not rerun destructive operational APPLY against a real production database during release preparation.

Verify that the release candidate still documents and preserves:

- immutable source checksum verification;
- explicit effective date;
- DRY_RUN before APPLY;
- zero-write DRY_RUN;
- full-file validation before writes;
- empty-target/import guard;
- atomic APPLY;
- final five-column `Retirada` compatibility from #168;
- adjusted opening quantity semantics using `BigDecimal`;
- no fabricated historical withdrawal movements;
- PostgreSQL-only operation after migration;
- operator review and post-import checks.

The release notes must make clear that #138 validated the real snapshot against disposable PostgreSQL, while the actual operational cutover remains a controlled operator procedure.

## Phase 7 — API contract audit

Audit the public `/api/v1` surface introduced or changed since `v0.4.0`.

Verify:

- existing v0.4.0 routes/contracts needed by V1 were not unintentionally broken;
- intentional additions are documented and additive where expected;
- catalog production metadata contracts remain compatible with legacy items that lack those values;
- formula, production registration, generated/manual lot, genealogy, and dashboard contracts match their implemented DTOs;
- stable error behavior remains coherent;
- JPA entities, Spring Data types, `Pageable`, or internal persistence structures do not escape public HTTP contracts;
- OpenAPI generation succeeds;
- documented wire enum/string values match implementation;
- frontend DTOs match backend responses without redefining domain authority.

Prefer contract/test evidence over manually duplicating schemas in release documentation.

## Phase 8 — Dependency and technology audit without upgrade churn

Review the actual backend `pom.xml`, frontend `package.json`, lockfile, and approved dependency documentation.

For each direct dependency, determine whether it is:

- actively required by runtime/product behavior;
- required by build/test/tooling;
- intentionally retained infrastructure with documented justification;
- apparently unused/speculative.

Important rule: finding an apparently unused dependency does not automatically authorize removal on the release branch.

Classify it using the finding policy. Remove only when the dependency creates a concrete release risk or when removal is a small, behavior-neutral stabilization with strong evidence.

Specifically check for:

- accidental duplicate HTTP/form/state/router libraries;
- eventing/messaging infrastructure with no concrete V1 use;
- runtime dependencies that exist only for hypothetical future work;
- manually pinned transitive versions that should be BOM-managed;
- development-only tooling leaking into production runtime;
- frontend dependencies not represented by actual V1 capabilities;
- lockfile/package-manager reproducibility.

Do not perform broad framework, Spring Boot, Angular, TypeScript, Testcontainers, or dependency upgrades as part of #139 unless a concrete compatibility/security blocker is proven.

## Phase 9 — Security and exposure audit

Release readiness and public-internet deployment readiness are not automatically the same thing.

Verify:

- no secrets or operational CSV data are committed;
- production configuration does not depend on dev-only Docker Compose lifecycle behavior;
- development tooling such as DevTools is not required for production operation;
- logs do not intentionally expose sensitive operational data;
- CORS/security behavior is understood for the intended deployment mode;
- Actuator/OpenAPI/Swagger exposure is understood and documented;
- backup/restore responsibilities are documented for the eventual operational environment.

Architecture documentation states that authentication is required before public exposure. Issue #139 explicitly keeps authentication/authorization and provider deployment out of scope unless a deployment/security decision makes them release blockers.

Therefore:

- if v0.5.0 is being published as a versioned artifact/repository release without public exposure, record missing deployment/auth decisions as operational constraints rather than inventing a security subsystem on the release branch;
- if the intended immediate deployment exposes Lavanda Flow publicly, unresolved authentication/authorization and secure production configuration become blockers and require a separate scoped decision/issue before exposure.

Do not silently add ad-hoc authentication in #139.

## Phase 10 — Documentation consistency audit

Review documentation against the implemented V1 state.

At minimum inspect:

- root/backend/frontend `AGENTS.md`;
- `README.md`;
- `docs/product/scope-v1.md`;
- `docs/domain/domain-model.md`;
- architecture/data-model/backend-structure/dependencies docs;
- ADR 0009;
- API documentation;
- initial-inventory operational documentation;
- V1 readiness report;
- Git/release workflow.

Identify stale future-tense statements that now contradict implemented behavior, such as production functionality described as merely “eventual” or UI paths described as future when they are already shipped.

Documentation corrections are allowed only where they make the release documentation accurate. Do not use the release branch for broad prose cleanup unrelated to v0.5.0 behavior.

Engineering documentation touched by #139 should be English according to current repository policy. Existing unrelated legacy-language documentation should not be mass-translated solely for release cleanliness; classify such cases unless the stale document is directly relied upon by the release procedure.

## Phase 11 — Quality, test, and build gates

### Backend

From `backend/`:

```bash
./mvnw verify
```

Mandatory evidence:

- build success;
- all tests green;
- Spring Modulith verification green;
- PostgreSQL/Testcontainers integration coverage green;
- no newly introduced blocker warning;
- CycloneDX/SBOM generation remains functional if part of the configured verify lifecycle.

Do not weaken production code or tests to make this pass.

### Frontend

From `frontend/`:

```bash
pnpm install --frozen-lockfile
pnpm lint
pnpm test
pnpm build
```

Mandatory evidence:

- reproducible lockfile install;
- lint success;
- all tests green;
- production build success;
- known bundle-budget warning assessed against #138 evidence;
- no new operator-facing English or accessibility regression in principal V1 flows.

The existing initial-bundle warning is not automatically a blocker. Treat it as a blocker only if the candidate introduces a material regression or the configured production build itself fails.

Do not force `pnpm test -- --run`.

### Repository checks

From repository root:

```bash
git diff --check
git status --short
```

Also inspect the complete release-branch diff against the cut point and against `main`/`v0.4.0`.

The release branch must contain only:

- version metadata;
- this release specification/audit evidence;
- release notes/documentation;
- explicitly justified release stabilization.

No feature work or unrelated refactor is allowed.

## Phase 12 — Release audit report

Create a concise release audit document, for example:

```text
docs/operations/v0.5.0-release-audit.md
```

Record at minimum:

1. release branch and audited HEAD SHA;
2. exact `develop` cut-point SHA;
3. included issue/PR set from `v0.4.0` to v0.5.0;
4. backend/frontend release versions;
5. architecture/module-boundary result;
6. domain-invariant result;
7. fresh Flyway installation result;
8. `v0.4.0 -> v0.5.0` migration result;
9. API compatibility/additions assessment;
10. dependency/technology assessment;
11. initial-inventory/cutover documentation assessment;
12. security/exposure constraints;
13. backend test count/result;
14. Spring Modulith result;
15. frontend lint/test/build result and test count;
16. known non-blocking warnings;
17. blocker list;
18. release stabilization changes, if any;
19. deferred non-blocking improvements;
20. final `READY` or `NOT READY` conclusion.

Do not mark `READY` while a mandatory gate is pending or a blocker remains unresolved.

## Phase 13 — Release notes

Prepare concise v0.5.0 release notes based on actual merged work.

They should cover materially useful operator/developer changes, including:

- initial inventory migration/cutover capability;
- operational dashboard;
- stable production metadata;
- formula setup;
- atomic internal production registration;
- generated/manual internal lot behavior;
- exact source-batch consumption and output batches;
- recursive genealogy;
- final V1 readiness validation;
- #168 final-snapshot compatibility correction;
- migrations/API additions;
- known operational constraints and warnings.

Do not expose private operational row data.

## Phase 14 — Release PR and merge

Only when the audit conclusion is `READY`:

1. push `release/v0.5.0`;
2. open `release/v0.5.0 -> main`;
3. use `.github/pull_request_template.md`;
4. title exactly:

```text
chore(release): prepare v0.5.0
```

5. include all relevant issues, migrations, API additions, validation results, risks, and operational notes;
6. assign `PxS00` and apply the release/repository metadata appropriate to the existing GitHub conventions;
7. ensure all required PR checks are green;
8. inspect complete diff, mergeability, comments, reviews, and review threads;
9. merge with a **regular merge commit** only — never squash or rebase.

The production merge commit becomes the release identity.

## Phase 15 — Tag and GitHub Release

After the release PR is merged to `main`:

- verify `main` points to the intended regular release merge commit;
- create tag `v0.5.0` on that exact merge commit;
- publish GitHub Release `v0.5.0` from that tag;
- verify backend version = `0.5.0`;
- verify frontend version = `0.5.0`;
- verify tag = `v0.5.0`;
- verify GitHub Release = `v0.5.0`.

Do not tag a release-branch commit before the production merge.

## Phase 16 — Post-release ancestry-preserving sync

After the release is published:

1. synchronize the production release merge back into `develop` while preserving that merge commit in `develop` ancestry;
2. include any release-branch stabilization that was not already in `develop`;
3. advance backend and frontend together to the next planned `X.Y.Z-SNAPSHOT` version in a dedicated follow-up issue/commit according to repository workflow;
4. validate the synchronized `develop` branch;
5. delete the release branch only after synchronization is confirmed.

Do not squash away or recreate the production release merge during back-sync.

## Acceptance criteria mapping

#139 is complete only when all of the following are true:

- all prerequisite V1 issues are complete;
- the release branch starts from the validated #138 `develop` head;
- version metadata is exactly `0.5.0` across backend/frontend;
- architecture remains a clean modular monolith matching ADR 0009;
- no module-boundary or reverse-dependency violation exists;
- no speculative architecture or unnecessary framework is introduced during stabilization;
- core inventory and production invariants remain demonstrably enforced;
- fresh PostgreSQL migration succeeds;
- real `v0.4.0 -> v0.5.0` Flyway upgrade succeeds without historical migration mutation;
- API additions/compatibility are audited;
- dependency usage is reviewed without upgrade churn;
- initial inventory/cutover safety remains documented and consistent with #138/#168;
- security/public-exposure constraints are explicitly understood;
- documentation materially relied upon for V1/release is consistent with implemented behavior;
- backend verify passes;
- Spring Modulith verification passes;
- frontend frozen-lockfile install, lint, test, and build pass;
- release audit concludes `READY`;
- release PR targets `main` and uses a regular merge commit;
- `v0.5.0` tag points to the production merge commit;
- GitHub Release `v0.5.0` is published;
- post-release `develop` sync preserves production merge ancestry.

## Allowed release-branch changes

Expected changes are limited to:

- `backend/pom.xml` release version;
- `frontend/package.json` release version and lockfile only if package metadata requires it;
- `docs/specs/0139-prepare-v0-5-0-release.md`;
- `docs/operations/v0.5.0-release-audit.md`;
- release notes or narrowly required operational/architecture documentation corrections;
- a demonstrably necessary release-stabilization fix explicitly classified under this specification.

Any production-code modification requires an explicit finding and justification. Feature work is forbidden.

## Out of scope

- new V1 features;
- v0.6.0 feature planning;
- broad refactoring or package reorganization;
- speculative architecture changes;
- microservices;
- event-driven redesign;
- adding queues/caches/graph databases/state-management frameworks;
- dependency modernization without a blocker;
- minimum-shelf-life policy;
- generic recurring CSV import;
- hosting/provider selection;
- ad-hoc authentication implementation;
- costing, sales, fiscal, purchasing, production planning, or broader ERP/manufacturing automation.

## Final implementation report

Before opening the release PR, report:

1. files created/modified;
2. audited candidate/cut-point SHAs;
3. prerequisite issue status;
4. version changes;
5. architecture findings;
6. dependency/technology findings;
7. fresh migration result;
8. `v0.4.0 -> v0.5.0` migration result;
9. API compatibility findings;
10. cutover/operations findings;
11. security/exposure findings;
12. documentation corrections;
13. backend validation/test count;
14. Spring Modulith result;
15. frontend lint/test/build/test count;
16. warnings and non-blocking debt;
17. blockers;
18. release stabilization performed and why;
19. unmet #139 acceptance criteria;
20. `git diff --check` result;
21. `git status --short`;
22. final `READY` or `NOT READY`.
