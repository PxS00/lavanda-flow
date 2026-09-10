# Issue #187 — Validate v0.6.0 local go-live readiness

## Specification status and timing

This specification was captured on 2026-09-10 **after** the automated and isolated portions of issue #187
acceptance had already been executed. It is not a pre-execution artifact and must not be cited as though it
authorized or predicted evidence before that evidence existed.

GitHub issue #187 remains authoritative for the objective, scope, acceptance criteria, constraints, and
out-of-scope behavior. This document records the approved validation boundary, classifies the evidence already
collected, and fixes the remaining evidence required before the readiness gate can change from `NOT READY`.

## Objective

Prove that the complete v0.6.0 candidate is ready for Céu de Lavanda daily operation in the accepted
local-first topology: authenticated, persistent, recoverable, restart-safe, lightweight, and usable from the
operator notebook and tablet without paid infrastructure.

This is a validation issue. It may compose and record existing capabilities; it must not hide product,
runtime, security, or operations feature work inside the acceptance branch.

## Source of truth

Apply in this order:

1. GitHub issue #187;
2. `AGENTS.md`, `backend/AGENTS.md`, and `frontend/AGENTS.md`;
3. ADR 0010: `docs/architecture/decisions/0010-adopt-local-first-operator-hosted-runtime.md`;
4. `docs/operations/local-operational-runtime.md`;
5. `docs/operations/local-go-live-runbook.md`;
6. `docs/operations/windows-operator-workstation.md`;
7. `docs/operations/postgresql-backup-restore.md`;
8. `docs/operations/initial-inventory-import.md`;
9. `docs/operations/v1-operational-readiness.md`;
10. `docs/operations/v0.6.0-go-live-readiness.md` for evidence recorded during the partial execution that
    preceded this specification;
11. this specification for the validation sequence and evidence-classification details that do not conflict
    with the sources above.

## Candidate and branch boundary

- Branch: `test/187/validate-v0.6.0-local-go-live-readiness`.
- Candidate already partially validated: `d5f2c9631bad7fc9fbac16255e133c7b90f2e37e`.
- The readiness report must identify the exact SHA for every fresh evidence campaign.
- If the candidate SHA changes after evidence is collected, assess the changed paths and rerun every affected
  acceptance path. Do not silently carry evidence across a materially changed candidate.
- Do not commit, push, or open a pull request unless separately requested after acceptance is complete.

## Evidence model

Every recorded result must be classified as one of:

- **fresh automated evidence** — commands or automated tests run against the stated candidate;
- **fresh manual evidence** — human-visible or physical-device behavior observed against the stated candidate;
- **fresh isolated acceptance evidence** — destructive or synthetic scenarios run against a disposable,
  uniquely identified PostgreSQL/runtime boundary;
- **reused evidence** — earlier evidence whose environmental assumption is explicitly confirmed unchanged;
- **not run** — required evidence that has not been executed against the stated candidate.

Reused evidence must name its source and tested revision. It must never be worded as a rerun or as fresh
candidate evidence. Source inspection, automated component tests, and HTTP-only checks do not substitute for
required physical notebook/tablet interaction.

## Execution status at specification capture

The following fresh evidence already exists for candidate
`d5f2c9631bad7fc9fbac16255e133c7b90f2e37e` and is recorded in
`docs/operations/v0.6.0-go-live-readiness.md`. Do not rerun it solely because this specification was captured
after execution:

- backend `./mvnw verify`: **passed**, 441 tests;
- frontend `pnpm lint`: **passed**;
- frontend `pnpm test`: **passed**, 57 files and 321 tests;
- frontend `pnpm build`: **passed**;
- fresh PostgreSQL startup with all 14 Flyway migrations: **passed**;
- isolated security, session, CSRF/XSRF, restart-session, and logout acceptance: **passed**;
- exact decimals, FEFO, expiration boundary, signed adjustments, physical losses, and expired disposal:
  **passed**;
- formula setup/read, production with exact source batches, output lot/batch creation, and recursive genealogy:
  **passed**;
- local image/runtime, health, persistence across application recreation, and full Compose restart: **passed**;
- isolated PostgreSQL backup, checksum, guarded restore, restored health, and representative relationship
  verification: **passed**.

Those scenarios used synthetic credentials, uniquely named Compose projects, temporary files, and disposable
PostgreSQL volumes. The real operational database and `.env.operational` were not used. The disposable runtime,
volumes, dumps, checksums, and temporary credentials were removed afterward.

The readiness conclusion at specification capture is **NOT READY**.

## Remaining required fresh evidence

The following evidence must still be executed against the exact current candidate before issue #187 can be
assessed `READY`:

1. current-candidate physical notebook validation on the prepared Samsung Galaxy Book;
2. current-candidate physical tablet validation through the stable trusted-LAN endpoint;
3. notebook and tablet visual/interaction acceptance for #199, #200, #201, #202, #207, and #209;
4. operator shortcut, browser close/reopen, and browser-independent runtime behavior;
5. current idle resource observation on the supported Windows/Docker Desktop host;
6. a current backup copy outside the notebook's primary failure domain and a checksum-verified round trip.

These are blocking evidence gaps. Creating this specification does not satisfy them and must not change the
readiness conclusion.

## Approved validation plan

### 1. Establish the candidate and safe boundary

Before each remaining campaign:

- record `git rev-parse HEAD`, branch name, and `git status --short`;
- confirm the candidate is the intended `develop` revision or record why it differs;
- confirm whether the prepared host, Docker Desktop/WSL 2 versions, trusted network, DHCP reservation,
  firewall rule, router exposure state, shortcut URL, and supported tablet are unchanged from #186;
- keep credentials, dumps, `.env` contents, real inventory rows, router credentials, MAC addresses, and account
  identifiers out of documentation, screenshots, logs, commits, and issue comments;
- do not create synthetic fixtures in the real operational database.

If representative writes are unsafe on the real operational database, use the already validated disposable
acceptance environment for those writes and use the physical devices to validate only the intended isolated
endpoint. Clearly record that split. Do not imply synthetic data was accepted into the real database.

### 2. Security and authenticated browser lifecycle

On both notebook and tablet, validate the accepted trusted-LAN HTTP transport:

- protected operator routes and APIs reject unauthenticated access;
- login and session bootstrap/reload work through the real Angular client;
- valid frontend writes carry CSRF/XSRF protection, while invalid state-changing requests remain rejected;
- cookie behavior matches ADR 0010 and the operational profile;
- logout removes effective access and the UI does not retain stale successful authenticated state;
- session invalidation/expiry returns the UI predictably to authentication without showing protected data;
- Swagger and non-health Actuator behavior match the operational profile;
- normal browser-visible responses and runtime logs reveal no secrets.

Do not weaken the session, cookie, CSRF, authorization, or operational-profile contracts to make acceptance
pass.

### 3. Notebook visual and interaction acceptance

Open Lavanda Flow through the real `Lavanda Flow` operator shortcut and verify:

- the branded shell, logo slot, favicon, spacing, visual hierarchy, and operator-facing pt-BR content are
  legible at the supported notebook viewport;
- #200/#207 helper text, contextual guidance, action terminology, validation messages, and empty states are
  concise and understandable without hiding essential instructions behind hover-only UI;
- #201 `essenceReference` and `productionTypeCode` values are visible at the relevant catalog, inventory, and
  production decision points without frontend-invented codes or wire-contract changes;
- #202/#209 navigation expands and collapses predictably with pointer and keyboard, preserves visible focus,
  does not clip content, and does not change or lose the active route;
- representative catalog, inventory, production, and genealogy reads render the backend result without
  duplicating FEFO, expiration, lot, or production authority in the frontend;
- exact six-decimal quantities remain readable and unchanged in representative UI flows.

Record viewport, browser, observed routes, pass/fail result, and any material usability note without capturing
credentials or real operational data.

### 4. Tablet visual and interaction acceptance

From the supported tablet on the trusted LAN, verify:

- the stable endpoint is reachable while the host is powered, awake, and connected;
- authentication and representative safe reads work;
- any representative write uses an explicitly safe database boundary and remains CSRF-protected;
- the branded shell, guidance, empty states, item references, quantities, and operational actions remain
  readable at the supported tablet viewport;
- collapsible navigation works by touch without hover, clipping, trapped/lost focus, accidental route changes,
  or an inaccessible control;
- returning after browser backgrounding/closure produces correct session behavior and no stale success state.

Record device/browser/viewport and the tested safe-data boundary. A desktop responsive emulator is supporting
evidence only; it does not replace this physical tablet pass.

### 5. Runtime, shortcut, availability, and persistence

On the real prepared workstation, validate the documented operator and maintainer paths:

- the operator shortcut opens the stable application URL without terminal, Docker UI, repository, Maven,
  pnpm, or IDE interaction;
- closing and reopening the browser does not stop the application or PostgreSQL services;
- application and normal runtime restarts preserve persisted data and the operator account;
- full Windows reboot plus sign-in returns Docker Desktop and the existing Compose services without developer
  intervention;
- automatic startup matches the runbook and `restart: unless-stopped` boundary;
- powered closed-lid, sleep, shutdown, and Wi-Fi disconnection behavior matches the documented availability
  limitation;
- health/status/start/stop/restart/update/backup/restore commands in the runbook contain no undocumented
  critical step;
- PostgreSQL remains unpublished on `5432`, the application firewall rule remains Private/LocalSubnet only,
  and no router forwarding/public exposure exists.

Do not use `docker compose down -v` against the operational project.

### 6. Current idle resource observation

With the supported workstation idle after startup and the application healthy:

- record application, PostgreSQL, and relevant Docker Desktop CPU/RAM observations;
- observe long enough to distinguish startup activity from continuous idle work;
- confirm there is no unnecessary third always-on application service or continuous abnormal CPU activity;
- record notable memory pressure for the 8 GB workstation without converting this issue into load testing.

The earlier Linux disposable-runtime snapshot is useful isolated evidence but does not satisfy this Windows
host requirement.

### 7. Current off-notebook backup evidence

Using the documented maintainer path:

- create or select a current operational backup and SHA-256 sidecar without exposing their contents;
- verify the checksum locally;
- place the dump and sidecar in the approved off-notebook failure domain;
- download/copy them back to a temporary verification location and verify the checksum again;
- record only non-sensitive timestamps/outcomes and the storage mechanism, not account identifiers, paths that
  reveal private data, dump contents, or credentials;
- remove temporary verification copies when appropriate.

Do not restore destructively over the real operational database for acceptance. Representative destructive
restore stays isolated and is already recorded as passed for the candidate.

## Reuse of #186 physical evidence

`docs/operations/windows-operator-workstation.md` records physical evidence against revision
`929634efb881b771c0c8376eac389e587d589502`.

It may be reused only as historical/environmental evidence where the maintainer confirms the assumption is
unchanged, for example the supported host identity, chosen Docker Desktop/WSL 2 architecture, intended
two-service topology, or documented trusted-LAN design. Each reused claim must:

- be labeled **reused evidence**;
- cite the #186 record and tested revision;
- state the unchanged assumption on which reuse depends;
- avoid language such as “reran”, “fresh”, or “passed on the current candidate”.

The #186 record cannot replace fresh validation of the current candidate's UI, real-device authentication,
shortcut/browser lifecycle, current restart/reboot behavior, current network exposure, current resource use,
or current off-notebook backup copy. Those conditions can drift, and the #186 revision predates
#199/#200/#201/#202/#207/#209.

## Blocker and stop policy

- This branch is validation-only; do not add hidden feature work.
- If an acceptance path reveals a production defect, stop that path, preserve non-sensitive evidence, and
  report the defect for a separate focused issue.
- Do not patch production code inside #187. Resume the affected acceptance path only after the focused fix is
  reviewed and the candidate SHA/evidence impact is reassessed.
- A tooling, environment, or evidence-availability problem must be recorded honestly as `NOT RUN` or blocked;
  do not convert source inspection or older evidence into a claimed current pass.
- Do not weaken tests, authentication, CSRF, stock invariants, FEFO, expiration, production, traceability,
  exact-decimal behavior, or backup safeguards.
- All destructive/read-write synthetic acceptance must use isolated/disposable PostgreSQL.
- The real operational database must never receive synthetic acceptance fixtures.
- Use the application `Clock`/business-date endpoint for date-sensitive scenarios.

## Acceptance and reporting rules

Update `docs/operations/v0.6.0-go-live-readiness.md` with:

- exact candidate SHA and validation date;
- files created/modified;
- fresh automated evidence;
- fresh manual notebook/tablet evidence;
- reused evidence with source revision and unchanged assumptions;
- isolated acceptance evidence and cleanup confirmation;
- blockers and focused follow-up issues;
- known non-blocking warnings and remaining risks;
- test/lint/build/runtime results;
- `git diff --check`, `git diff`, and `git status --short`;
- final `READY` or `NOT READY` assessment.

The report may change to **READY** only when all remaining required fresh evidence passes against the exact
candidate, every production blocker has been resolved separately, and no acceptance criterion remains
unproven. Until then it must remain **NOT READY**.

## Validation commands

Run automated and isolated validation only when candidate changes or an affected path requires a rerun:

```text
backend/  ./mvnw verify
frontend/ pnpm lint
frontend/ pnpm test
frontend/ pnpm build
```

The repository/runtime campaign includes the documented no-cache operational image build, required-variable
failure behavior, exact two-service topology, fresh Flyway startup, operational health/security, persistence,
backup/checksum, and guarded disposable restore.

Before final handoff, always run:

```text
git diff --check
git diff
git status --short
```

Do not print fully rendered Compose configuration, `.env` contents, credentials, or dump contents.

## Out of scope

- new product features or UI redesign;
- production-code fixes inside this acceptance issue;
- cloud/public deployment, registry publication, or paid infrastructure;
- PWA/offline behavior;
- router automation;
- high availability, replication, or point-in-time recovery;
- load/performance testing beyond practical supported-notebook resource observation;
- minimum-shelf-life policy #73;
- costs, margins, sales, purchasing, fiscal behavior, notifications, or analytics.

## Completion condition

Issue #187 is complete only when the automated/isolated evidence remains applicable to the final candidate,
all remaining physical notebook/tablet and off-notebook backup evidence is freshly recorded, any blocker is
resolved through separate focused work, the real operational database remains free of synthetic acceptance
fixtures, and the readiness report can truthfully conclude **READY**.
