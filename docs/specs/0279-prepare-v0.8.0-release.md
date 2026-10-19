# Issue #279 — Prepare v0.8.0 release

## Authority and objective

[Issue #279](https://github.com/PxS00/lavanda-flow/issues/279) is the source of truth, refined by
[spec #259](0259-define-v0.8-commercial-scope.md), the [commercial scope](../product/scope-v0.8.0.md),
[ADR 0012](../architecture/decisions/0012-define-v0.8-commercial-boundaries.md), and implementation
specifications [#260](0260-manage-customer-contacts.md), [#261](0261-register-customer-orders.md),
[#262](0262-confirm-orders-with-fefo-withdrawal.md), [#275](0275-expose-paginated-sale-history.md),
and [#276](0276-register-sale-linked-stock-corrections.md).

Prepare the delivered commercial extension for release PR review. The initial preparation pass
excluded commits, pushes and PR creation; subsequent authorization published the preparation commit
and opened [Release PR #280](https://github.com/PxS00/lavanda-flow/pull/280) targeting `main`.
This documentation follow-up permits no commit, push, merge, tag, GitHub Release, deployment,
credentials or operational data access.
No product feature, dependency upgrade, unrelated refactor or historical migration change is authorized.

## Fixed baseline and included work

- Branch: `release/v0.8.0`.
- Integrated release base: `728dd8687636bd03f5528ba171f84315971e27e5`.
- Published preparation commit / PR #280 head: `cfcd9aed289fd1989d9a90829f60f0e6b211c61f`.
- Production `main`: `c9376ab3efb342b92826c342db72fbd1395fce00` (v0.7.1).
- At the baseline audit, refreshed `origin/develop` equalled the integrated base; production was its
  ancestor, with 10 commits ahead and zero behind. Local `develop` was at `149e2fa380806d9996484db557008744aa944b25`;
  do not mistake its nine-commit delta for the complete release.
- Required: #259 scope, #260 contacts, #261 drafts, #262 atomic confirmation, #275 history,
  #276 audited stock corrections. The delta also includes #254 back-sync, #268/#270 repository
  governance and #271 monitoring implementation.
- #263 fiscal feasibility is optional and nonblocking. #273 live monitor activation requires the
  workflow on default branch `main` and remains post-release; mocked checks cannot fulfill it.

## Change map and ownership

Only product metadata (`backend/pom.xml`, `frontend/package.json`) and this spec plus
`docs/operations/v0.8.0-release-readiness.md` should need changes. Both product versions become
exactly `0.8.0`, without SNAPSHOT. The pnpm v9 lockfile's root importer records dependencies, not
the root package version; retain it byte-for-byte unless actual metadata consistency requires otherwise.
No runtime caller, DTO, fixture, export, dependency or test behavior should change for a metadata release.

Audit current readers/writers before accepting the release:

| Owner | Readers/writers and invariants |
| --- | --- |
| `customers` | Validated contact maintenance/search, immutable public lookup, history-preserving deactivation |
| `sales` | Exact draft pricing, stable line IDs, optional active customer, locked lifecycle, persisted snapshots/allocations, paginated history, correction identity and cumulative limits |
| `inventory` | Public transaction-joining sale withdrawal/return APIs; exclusive FEFO, current eligibility/expiry, item/batch locks, balances and immutable movements |
| `shared` | Existing session, OPERATOR authorization, CSRF, error shape, Clock and exact-decimal JSON |
| Angular features | Existing typed HTTP clients and pt-BR operator workflows; no database access, stock authority or optimistic stock effects |

Confirm retries return stored snapshots without a new withdrawal. Correction retries preserve the
same sale-scoped ID and normalized payload; concurrent distinct returns cannot exceed the original
line/batch allocation. Confirmed snapshots and original movements remain immutable. Cancellation is
draft-only, has no stock effect, and is intentionally an API-only action in the delivered #262 scope.

## Database transition and recovery contract

Audit all V1–V20 SQL and the actual migration regression tests. V1–V16 must match production bytes;
V17–V20 must match their integrated baseline bytes. PostgreSQL/Testcontainers only; Hibernate must
validate, never create/evolve the schema. No operational Supabase connection is allowed.

| Stage | Data shape and compatibility |
| --- | --- |
| V17 | Add customer contacts/index; legacy tables and writers preserved |
| V18 | Add sales orders/lines and paging indexes; drafts have no stock effects |
| V19 | Add nullable movement references, confirmation snapshots, allocations and immutable-history triggers |
| V20 | Add immutable accepted correction audit, FKs and identity; create non-return uniqueness index before dropping V19's broad index in one migration transaction |

Forward path: apply additive migrations through Flyway, validate schema, then route new commercial
and correction traffic to the new backend before deploying the matching UI. Legacy inventory writers
omit nullable references; V20 still rejects duplicate CONSUMPTION. Verify clean V1→V20 plus legacy
V16/V17/V18/V19 upgrades, retained rows/genealogy, old/new writers and no-op migration retries.
Failed migration DDL rolls back; application transaction failures leave no partial balance/audit effect.

Rollback is application rollback with expanded schema and data retained, subject to maintainer review
and old-binary Flyway compatibility checks. Old UI/backend versions lose new history/correction access;
rollback does not reverse committed withdrawals or accepted returns. Never recreate the V19 broad
index after partial returns exist. No down migration, deletion, backfill or contraction is authorized.
Committed-data recovery requires a verified protected PostgreSQL 17 backup and a separately reviewed
restore, under the [recovery contract](../operations/postgresql-backup-restore.md).

## Validation and acceptance

1. Reconcile full `origin/main..HEAD` commits, issue states, actual APIs and Angular consumption;
   preserve historical V1 and document intentional API-only contracts and nonblocking follow-ups.
2. Align both versions and prove no dependency/lockfile drift.
3. Run `./mvnw verify` in backend and `pnpm lint`, `pnpm test`, `pnpm build` in frontend.
   Record counts and warnings from this run; retain all production invariants and tests.
4. Verify Modulith, exact decimals, FEFO/expiry/eligibility, transactional/nonnegative stock,
   immutable history, confirmation/correction retry, cumulative concurrency, auth/CSRF and OpenAPI
   through existing focused assertions in the complete suites.
5. Run existing backup, disposable-restore and credential-free monitoring contract tests; replay
   repository required-file/whitespace and synthetic operational Compose checks. Attempt the existing
   no-cache image build when Docker is available, without loading operational environment files.
6. Inspect `git diff --check`, `git diff develop...HEAD`, `git status --short`, the full release delta,
   and all preparation changes including untracked documents. The stale local develop diff includes
   #276; separately compare refreshed `origin/develop` and the fixed release base.
7. Record actual results, distinct integrated-base and published preparation SHAs, API/migration
   risks, warnings, blockers and pending operational gates in the readiness document. Keep original
   pre-commit local validation distinct from verified GitHub Actions results for the PR head SHA.

Repository readiness requires all applicable checks passing and no unresolved release blocker.
Release-PR CI remains mandatory: `validate-title`, `repository-quality`, `Maven verify`,
`Angular verify`. PR #280 uses `release/v0.8.0 -> main` and title
`chore(release): prepare v0.8.0`; merge requires the official PR template and a regular merge commit,
never squash. The [readiness record](../operations/v0.8.0-release-readiness.md) records the observed
current-head checks separately from the original local evidence; those results do not cover this
uncommitted documentation follow-up.

## Deferred gates

After review, release CI, merge and immutable tag publication, the maintainer follows the
[go-live runbook](../operations/local-go-live-runbook.md): healthy baseline, protected pre-upgrade
dump/checksum and verified off-notebook copy, disposable restore, exact-tag upgrade, V20/schema/health
and authentication checks, representative inventory/production/commercial smoke checks, notebook/tablet
LAN and cryptographic correction-ID behavior, reboot recovery and follow-up backup. Check #273's
dedicated least-privilege identity, TLS, manual dispatch, notifications and first scheduled run only
after the monitoring workflow reaches main. Back-sync must preserve ancestry and advance both
development versions together to the next approved SNAPSHOT. None of these operational/publication
gates is established by repository-only validation.

## Repository preparation result

Completed on 2026-10-19: both product versions are `0.8.0`, backend verify passed 722 tests and
frontend passed lint, 518 tests and production build. Migration byte comparisons, legacy upgrades,
Modulith, operational contracts, synthetic Compose checks, no-cache image build and a disposable
operational-profile V20/Hibernate-validate/health startup passed. Detailed scope, counts, warnings,
compatibility limits and pending gates are in the [readiness record](../operations/v0.8.0-release-readiness.md).
The original preparation changed only the two manifests and two release documents and was published
as `cfcd9aed289fd1989d9a90829f60f0e6b211c61f`, above integrated base
`728dd8687636bd03f5528ba171f84315971e27e5`. Release PR #280 targets `main`.
The validation counts above are the original pre-commit local evidence, not a claim about remote CI.
Merge, tag, GitHub Release and operational acceptance remain pending. This follow-up changes only
the two release documents and does not establish production deployment readiness.
