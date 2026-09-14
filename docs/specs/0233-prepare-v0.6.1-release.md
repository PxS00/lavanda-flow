# Issue #233 — Prepare v0.6.1 release

## Status

Approved for implementation.

## Source of truth

- GitHub issue: #233 `chore(release): prepare v0.6.1`
- Release branch: `release/v0.6.1`
- Release branch base: `develop` at `a62d381a4ba1b225f6d9e7b5552980514cea8c09`
- Product release: `v0.6.1 — Operational Inventory Correction`

This specification refines the repository-preparation portion of #233. The GitHub issue remains authoritative if a conflict is discovered.

## Objective

Prepare Lavanda Flow v0.6.1 as the first practical corrected finished-product baseline for Céu de Lavanda, preserving the completed finished-product/import/filling work and producing a validated, reproducible release candidate suitable for a release PR to `main` and the subsequent controlled operator cutover.

The release must not add new product capabilities. It packages and validates the already merged scope.

## Included completed work

The release candidate must include the completed work from:

- #229 — finished-product model, stable fragrance references, and product gender;
- #231 — corrected initial bulk-perfume snapshot mapping;
- #235 — packaged-product lot and genealogy convention;
- #232 — packaged-product filling workflow.

All four issues are already merged into `develop` before this release branch was created.

## Current repository state

At branch creation:

- `develop` head is `a62d381a4ba1b225f6d9e7b5552980514cea8c09`;
- backend Maven version is `0.7.0-SNAPSHOT`;
- frontend package version is `0.7.0-SNAPSHOT`;
- the release branch must publish both as exactly `0.6.1`;
- the existing normal internal generated lot contract remains `TTT-EEE-LLL-MM-YYYY`;
- packaged generated lots use `SSS-MM-YYYY`;
- the corrected operational CSV remains external to source control.

The apparent version movement from `0.7.0-SNAPSHOT` on `develop` to `0.6.1` on the release branch is intentional release metadata alignment, not a dependency downgrade.

## Architecture and invariant constraints

Preserve all existing architecture and domain invariants:

- modular monolith and Spring Modulith boundaries;
- PostgreSQL as the source of truth;
- Flyway as the only schema migration authority;
- quantities represented with `BigDecimal`;
- no negative stock;
- immutable/auditable stock history;
- transactional stock balance changes;
- FEFO, expiration, source allocation, formula scaling, genealogy, and lot allocation remain backend-authoritative;
- `expiresAt <= today` remains expired;
- date-sensitive rules/tests use the application `Clock`;
- no business rules move into controllers or Angular components.

Do not alter these rules during release stabilization.

## Repository preparation scope

### 1. Release version metadata

Set the product release version to exactly `0.6.1` on `release/v0.6.1`:

- backend Maven project version: `0.6.1`;
- frontend package version: `0.6.1`.

Inspect `frontend/pnpm-lock.yaml`. Modify it only if the package-manager contract actually requires a version change. Do not create lockfile noise merely to mirror `package.json`.

Do not change dependency versions as part of the release version update.

### 2. Release-scope regression validation

Validate the already merged v0.6.1 behavior without redesigning it.

At minimum, evidence must cover:

#### Finished-product/catalog baseline

- `FINISHED_PRODUCT` is represented correctly in backend, persistence, HTTP contracts, and Angular presentation;
- formulated bulk product may use `MILLILITER`;
- packaged presentations may use `UNIT`;
- shared stable `essenceReference` semantics remain intact;
- persisted product gender semantics remain intact.

#### Corrected initial import

Using only synthetic/disposable PostgreSQL state for automated validation:

- the eight-column corrected snapshot contract is accepted;
- bulk perfumes import as `FINISHED_PRODUCT`, never `ESSENCE`;
- positive stock rows require and persist source `LotCode`;
- `EssenceReference`, `ProductionTypeCode`, and gender are validated and persisted;
- multiple source lots for one explicit product identity do not create duplicate catalog identities;
- `Retirada`, expiration, exact decimal quantities, deterministic reporting, empty-target protection, `DRY_RUN`, and atomic `APPLY` remain intact;
- no historical supplier, receipt, withdrawal, production execution, formula, or genealogy is fabricated.

Do not use the real operational CSV or the real operational database for repository validation.

#### Packaged filling

Validate the existing #232 implementation:

- a `PACKAGED_FILLING` formula may combine bulk finished product and packaging/components in their own persisted units;
- scaling is exact and backend-authoritative;
- one successful execution creates one packaged output batch and auditable history;
- exact source batches are persisted as genealogy;
- multiple bulk source batches are supported when legitimately consumed;
- packaged generated lots use `SSS-MM-YYYY`;
- the packaged sequence is backend allocated, global per calendar month/year, and resets for a new month/year;
- Angular neither computes nor reserves the definitive packaged sequence;
- insufficient/expired/ineligible source stock rolls back atomically;
- existing normal internal production still generates `TTT-EEE-LLL-MM-YYYY`;
- no automatic packaged-product expiration derivation exists.

### 3. Flyway and upgrade safety

Audit schema evolution for the release candidate.

Expected relative to the v0.6.0 release baseline:

- historical migrations through V14 remain unchanged;
- V15 contains the finished-product/shared-reference schema evolution from #229;
- V16 contains packaged-product filling support from #232;
- #231 does not require a schema migration;
- no historical migration may be edited, reordered, or replaced.

Verify a fresh PostgreSQL database can migrate through the complete current migration chain and that application schema validation succeeds.

If an unexpected migration or checksum drift is found, report it as a release blocker rather than silently repairing history.

### 4. Release readiness evidence

Create `docs/operations/v0.6.1-release-readiness.md` as the release-candidate evidence record.

It must be factual and distinguish:

- automated checks actually rerun on the release branch;
- synthetic/disposable integration evidence;
- evidence inherited from already merged issue tests;
- operational steps that remain pending until the real operator cutover;
- known non-blocking warnings;
- release blockers, if any.

Do not claim that a physical/operator workflow was rerun unless it actually was.

The readiness document should summarize:

- release candidate commit under validation;
- included issues #229, #231, #235, #232;
- backend/frontend version alignment;
- migration inventory and compatibility result;
- corrected import contract status;
- packaged filling/lot/genealogy status;
- full backend/frontend validation results;
- repository/diff validation;
- remaining controlled cutover steps.

### 5. Operational documentation consistency

Review the existing operational documentation affected by this release, especially:

- corrected initial-inventory import procedure;
- PostgreSQL backup/restore procedure;
- local go-live/runtime procedure;
- packaged-product filling technical documentation.

Only change those files if a concrete contradiction with v0.6.1 is found. Do not rewrite stable documentation for style.

The real corrected CSV, its real checksum, credentials, private paths, backups, and operator data must remain outside source control.

## Required validation

Run the complete release-branch validation:

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

- backend resolved project version is exactly `0.6.1`;
- frontend package version is exactly `0.6.1`;
- no `-SNAPSHOT` remains in release product metadata;
- backend/frontend product versions match;
- Spring Modulith verification remains green;
- Flyway migration history is append-only;
- no unrelated feature or v0.7.0 work entered the branch.

If Docker is available, validate the documented operational package/runtime only with isolated synthetic resources. Never reuse or destroy the real operational PostgreSQL volume.

## Release PR contract

After local/repository preparation is reviewed and committed, the release PR must be:

- head: `release/v0.6.1`;
- base: `main`;
- title: `chore(release): prepare v0.6.1`;
- ready for review, never draft;
- based on `.github/pull_request_template.md`;
- assigned to `PxS00`;
- labeled consistently for release/repository work;
- associated with milestone `v0.6.1` where supported.

The PR must summarize:

- included issues;
- migrations V15 and V16;
- API/behavior changes;
- release validation evidence;
- known non-blocking warnings;
- operational correction/cutover notes.

The release PR to `main` must use a regular merge commit, never squash or rebase.

## Post-merge release steps

These steps are not part of the initial Codex repository-editing task. They happen only after the release PR is reviewed and merged:

1. confirm the production merge commit on `main`;
2. create tag `v0.6.1` on that production merge commit;
3. publish GitHub Release `v0.6.1`;
4. verify the immutable tagged revision;
5. perform the controlled operator-notebook upgrade from the tag;
6. preserve the PostgreSQL volume;
7. restore the approved known-good pre-import database state before corrected APPLY, provided the operational-use precondition still holds;
8. prepare the corrected external CSV and new SHA-256 outside source control;
9. run corrected `DRY_RUN` and require `rejected=0` plus operator review;
10. run one corrected `APPLY`;
11. verify representative products, source lots, balances, expiration, and opening `ENTRY` history;
12. run one representative bulk-to-packaged filling smoke flow and verify packaged lot plus genealogy;
13. create and verify a post-import/post-smoke backup outside the notebook failure domain;
14. back-sync the production merge ancestry into `develop` without squashing it;
15. ensure `develop` returns to the planned next development snapshot metadata after the release synchronization.

Do not execute destructive operational actions from a generic development environment.

## Constraints

- No new features on the release branch.
- No #230 grouped stock workspace work.
- No #222, #223, #224, #225, #226, or #228 work.
- No sales/order/POS/customer/pricing/payment scope.
- No barcode/QR scope.
- No automatic packaged shelf-life derivation.
- No dependency upgrades unless a release-blocking defect proves one is strictly necessary and is explicitly reviewed.
- Do not modify historical Flyway migrations.
- Do not weaken production code or domain invariants to make release tests pass.
- Never run `docker compose down -v` against the operational runtime.
- Never commit the real operational CSV, credentials, dumps, checksums containing private paths, or private operator data.

## Out of scope for the Codex implementation pass

The first implementation pass must not:

- create or merge the GitHub release PR;
- create the `v0.6.1` tag;
- publish the GitHub Release;
- operate the mother's real notebook/runtime;
- restore the real operational database;
- run the real corrected CSV import;
- create/delete real operational volumes;
- perform the post-release back-sync;
- advance `develop` metadata.

Those steps require review of the release-candidate evidence first.

## Acceptance criteria for repository preparation

The repository-preparation phase is complete when:

- backend and frontend versions are exactly `0.6.1`;
- no dependency drift was introduced;
- the v0.6.1 scope from #229, #231, #235, and #232 is represented and regression-validated;
- V15/V16 are present and historical Flyway migrations remain unchanged;
- `./mvnw verify` passes;
- `pnpm lint` passes;
- `pnpm test` passes;
- `pnpm build` passes;
- `git diff --check` passes;
- the full release-branch diff contains only intended release preparation/stabilization changes;
- `docs/operations/v0.6.1-release-readiness.md` records factual evidence and remaining operational steps;
- no unresolved release blocker remains;
- `git status --short` is reported for review.

## Implementation principle

Make the smallest release-preparation change that satisfies #233. Prefer evidence and validation over refactoring. If validation discovers a genuine release-blocking defect, report it clearly and fix only the minimum required behavior on the release branch with dedicated regression coverage.