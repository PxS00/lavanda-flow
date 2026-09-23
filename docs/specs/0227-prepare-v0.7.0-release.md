# Issue #227 — Prepare v0.7.0 release

## Status

Approved for release preparation.

## Source of truth

- GitHub issue: #227 `chore(release): prepare v0.7.0`
- Release branch: `release/v0.7.0`
- Release branch base: `develop` at `74d9f7020daba714ba3dc54f1253f06ecaa8f635`
- Product release: `v0.7.0 — Operational Autonomy`

This specification refines the repository-preparation phase of #227. The GitHub issue remains authoritative if a conflict is discovered.

## Objective

Prepare Lavanda Flow v0.7.0 as a focused operational-autonomy release without adding new product scope.

The release packages and validates the already merged work from:

- #222 — safe inventory-item maintenance;
- #223 — supplier maintenance;
- #224 — actionable inventory alerts;
- #225 — production execution history;
- #226 — automated operational PostgreSQL backups;
- #230 — grouped operational stock workspaces.

v0.6.1 remains the functional baseline for finished products, corrected initial import, packaged filling, packaged lots, and genealogy.

The real operational business dataset is still uninitialized. The first real business-data cutover is intentionally deferred until after the immutable v0.7.0 tag is published.

## Current repository state

At branch creation:

- `develop` head: `74d9f7020daba714ba3dc54f1253f06ecaa8f635`;
- backend version: `0.7.0-SNAPSHOT`;
- frontend version: `0.7.0-SNAPSHOT`;
- release branch must publish both as exactly `0.7.0`;
- all included implementation issues are merged;
- release-governance documentation is aligned;
- live `develop` and `main` rulesets require:
  - `validate-title`;
  - `repository-quality`;
  - `Maven verify`;
  - `Angular verify`.

## Architecture and invariant constraints

Preserve:

- modular monolith and Spring Modulith boundaries;
- PostgreSQL as source of truth;
- Flyway as the only schema-evolution mechanism;
- `BigDecimal`/exact decimal quantities;
- non-negative stock;
- immutable and auditable stock history;
- transactional stock balance operations;
- backend-authoritative FEFO, expiration, eligibility, source allocation, formula scaling, genealogy, and lot allocation;
- `expiresAt <= today` as expired;
- application `Clock` for date-dependent rules/tests;
- no business logic in controllers or Angular components.

Do not add speculative modules, abstractions, dependencies, events, analytics, sales/POS, purchasing, costing, or cloud services.

## Repository preparation scope

### 1. Release version metadata

Set product version to exactly `0.7.0`:

- backend Maven project version: `0.7.0`;
- frontend package version: `0.7.0`.

Inspect `frontend/pnpm-lock.yaml`; change it only if required by the package-manager metadata contract.

Do not change dependency versions.

### 2. Release-scope audit

Validate the merged v0.7.0 capabilities without redesigning them.

Confirm:

- Catálogo and Estoque remain separate operator concepts;
- grouped stock routes/workspaces cover finished products, essences, inputs, packaging/components, and all stock;
- inventory-item maintenance preserves stable references and history;
- supplier maintenance is non-destructive;
- alerts navigate to existing backend-authoritative workflows;
- production execution history and detail preserve persisted source allocations;
- every new operator-facing v0.7.0 backend endpoint has an Angular workflow;
- intentionally non-UI endpoints remain classified as lower-level/internal/technical/maintainer contracts;
- FEFO and other inventory authority remain in the backend;
- exact-decimal transport remains intact;
- user-visible application content remains pt-BR.

### 3. v0.6.1 regression validation

Verify, without reimplementing:

- `FINISHED_PRODUCT`;
- shared stable fragrance references;
- product gender;
- corrected eight-column initial-import contract;
- normal generated lot format `TTT-EEE-LLL-MM-YYYY`;
- packaged generated lot format `SSS-MM-YYYY`;
- packaged filling through the existing production module;
- explicit source-batch genealogy;
- atomic rollback on production failure;
- no automatic packaged-product expiration derivation.

Repository validation must use synthetic/disposable data only.

Do not run the real operational CSV during release preparation.

### 4. Flyway and schema safety

Audit the current migration chain.

Requirements:

- historical migrations remain unchanged;
- Flyway is append-only;
- a fresh PostgreSQL database migrates through the full current chain;
- application schema validation succeeds;
- no unexpected migration/checksum drift exists.

Treat any historical migration change or checksum drift as a release blocker.

### 5. Backup/operations validation

Validate the repository-side #226 behavior and operational documentation.

The real Windows host already demonstrated a successful scheduled backup execution before release preparation, including:

- Docker/PostgreSQL readiness;
- authoritative `backup-postgres.sh` invocation;
- verified local dump + SHA-256;
- verified external copy;
- safe retention gating;
- successful exit code.

The temporary pre-release scheduled task was intentionally removed afterward.

Do not reinstall the final production task during repository preparation. Final installation belongs to the post-tag first cutover.

### 6. Release readiness evidence

Create or update:

`docs/operations/v0.7.0-release-readiness.md`

Record factual evidence only:

- release candidate commit;
- included issues;
- version alignment;
- backend/frontend validation;
- module-boundary result;
- Flyway/schema result;
- operator-capability audit;
- v0.6.1 regression result;
- backup automation validation;
- repository/ruleset governance state;
- known non-blocking warnings;
- post-release first-cutover steps still pending.

Do not claim physical notebook/tablet or real business-data cutover checks that have not yet been executed.

## Required validation

Backend:

```bash
cd backend
./mvnw verify
```

Frontend:

```bash
cd frontend
pnpm lint
pnpm test
pnpm build
```

Repository:

```bash
git diff --check
git diff develop...HEAD
git status --short
```

Also verify:

- backend resolved version is exactly `0.7.0`;
- frontend version is exactly `0.7.0`;
- no `-SNAPSHOT` remains in release product metadata;
- backend/frontend versions match;
- Spring Modulith verification is green;
- Flyway migration history is append-only;
- no unrelated feature work entered the release branch.

Known non-blocking warnings must be recorded rather than silently expanding scope.

## Release PR contract

After repository preparation is reviewed and committed:

- head: `release/v0.7.0`;
- base: `main`;
- title: `chore(release): prepare v0.7.0`;
- ready for review, never draft;
- use `.github/pull_request_template.md`;
- assignee: `PxS00`;
- labels/milestone consistent with #227;
- regular merge commit only, never squash/rebase.

The PR must summarize:

- included issues;
- migrations/schema impact;
- API/operator behavior;
- validation evidence;
- known non-blocking warnings;
- first-cutover operational notes.

## Post-merge first operational cutover

These steps are explicitly outside the repository-preparation implementation pass.

Only after the release PR is merged and tag `v0.7.0` is published:

1. record the production merge/tag SHA;
2. create and verify a fresh backup of the current operational PostgreSQL state;
3. copy and checksum-verify it outside the notebook failure domain;
4. deploy the immutable `v0.7.0` tag without deleting/replacing the PostgreSQL volume;
5. verify Flyway/schema, health, authentication, LAN access, and PostgreSQL port isolation;
6. validate the external corrected CSV against the #231 eight-column contract;
7. verify the approved/new SHA-256;
8. run operational `DRY_RUN` and require `rejected=0`;
9. manually review representative identity, gender, lot, quantity, and expiration values;
10. verify the CSV checksum again;
11. execute exactly one accepted `APPLY`;
12. verify representative `FINISHED_PRODUCT` items, source lots, balances, expiration, and opening `ENTRY` history;
13. execute a representative packaged-filling smoke flow;
14. verify generated `SSS-MM-YYYY` lot and persisted genealogy;
15. smoke notebook workflows;
16. smoke tablet/LAN workflows;
17. verify reboot auto-recovery;
18. install the v0.7.0 scheduled backup task;
19. run and verify a post-cutover backup plus off-notebook copy;
20. declare Lavanda Flow/PostgreSQL the operational source of truth and freeze the CSV;
21. perform ancestry-preserving back-sync to `develop`;
22. advance backend/frontend together to the next selected development snapshot.

Never run `docker compose down -v` against the operational runtime.

## Constraints

- No new features on the release branch.
- No redesign of #231, #232, or #235.
- No real operational CSV import during repository preparation.
- No real notebook cutover during repository preparation.
- No production scheduled-task installation during repository preparation.
- No dependency upgrades unless a concrete release blocker requires one and it is explicitly reviewed.
- Do not modify historical Flyway migrations.
- Do not weaken production code to make tests pass.
- Do not commit credentials, dumps, real CSV data, private paths, or operator data.
- Do not create/merge the release PR, tag, GitHub Release, or back-sync from the Codex implementation pass unless explicitly instructed afterward.

## Acceptance criteria for repository preparation

Repository preparation is complete when:

- backend/frontend versions are exactly `0.7.0`;
- no dependency drift was introduced;
- all merged v0.7.0 capabilities are regression-validated;
- v0.6.1 finished-product/import/filling/lot/genealogy contracts remain non-regressed;
- Flyway/schema validation passes with no historical migration drift;
- `./mvnw verify` passes;
- `pnpm lint`, `pnpm test`, and `pnpm build` pass;
- repository checks pass;
- known non-blocking warnings are documented;
- `docs/operations/v0.7.0-release-readiness.md` contains factual release evidence;
- final diff contains only release-preparation changes;
- `git diff --check` passes;
- `git status --short` contains only intentional release-preparation changes.
