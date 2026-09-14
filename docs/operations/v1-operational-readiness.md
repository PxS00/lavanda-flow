# V1 operational readiness

> **Historical v0.6.0 evidence.** The real-cutover interpretation recorded below was later found to be wrong for Céu de Lavanda's bulk perfume stock. Issue #231 and `docs/specs/0231-correct-initial-bulk-perfume-snapshot-mapping.md` supersede the five-column source contract, `ESSENCE` mapping, null-lot assumption, and the frozen checksum for the corrected operational cutover. The counts and checks below remain evidence of what was tested on 2026-09-06; they are not approval to reuse that source or mapping. Corrected v0.6.1 cutover evidence belongs to #233.

## Tested revision

- Branch: `test/138/validate-v1-operational-readiness`
- HEAD commit: `7c568ccb3ce31954984d33061199c959adabf0f8`
- Develop includes `79f4353d8a8034740b3390ea27119c7e293cdb49` (`#168`).
- Acceptance tests and this report were executed from the uncommitted issue #138 working tree on 2026-09-06.

## Automated acceptance

### Backend — PASSED

`V1OperationalReadinessAcceptanceTest` passed against PostgreSQL 17 through Testcontainers with a fixed
application clock. The scenario used the then-approved synthetic #134-shaped CSV. The test fixture is updated
by #231 to the revised eight-column source so current acceptance no longer exercises an obsolete contract.
The historical run proved:

- `DRY_RUN` performs zero database writes and `APPLY` creates the expected catalog, opening batches, and
  auditable entry movements;
- subsequent balances, batches, history, receipt, and FEFO operations use PostgreSQL after the CSV is
  removed;
- dashboard and low-stock/expiration alerts remain coherent with the constructed inventory state;
- two internal productions consume exact persisted batch identities, use backend-generated definitive lots,
  create one output batch each, preserve immutable movements, and never create negative stock;
- the first internally produced output is accepted as the second production's normal inventory input;
- upstream and downstream genealogy traverse both production levels through backend relationships and stable
  IDs, reaching the original external source batch.

Focused backend coverage remains unchanged and was reused for failure, rollback, concurrency, and detailed
validation branches.

### Frontend — PASSED

`app.v1-operational-readiness.spec.ts` passed using the real Angular router, application shell, feature
components, typed API clients, and `HttpTestingController` only at the HTTP boundary. It composes dashboard,
production setup inspection, production registration, backend-confirmed inventory refresh, and recursive
genealogy. It verifies pt-BR operator copy, exact source-batch identity, generated lot mode without a
frontend-authored authoritative sequence, no success before the POST response, and genealogy rendered from
backend response data. Existing `app.operational-workflow.spec.ts` coverage remains intact.

## Real operational CSV — HISTORICAL v0.6.0 EVIDENCE

The following source/checksum is retained only to document the first technically successful but semantically
incorrect v0.6.0 import validation. **Do not use it for the corrected v0.6.1 cutover.**

- File: `inventory-snapshot-2026-09-06.csv`
- Historical SHA-256: `0d2c799d1efdb5d0924c36612b449ac6e42275da49f2d35c153e35165d2793b8`
- Historical checksum verification: **PASSED** before `DRY_RUN`, immediately before `APPLY`, and after acceptance.
- Effective date: `2026-09-06`, the explicit date when Lavanda Flow established the opening snapshot; it is
  not a fabricated historical purchase, receipt, or withdrawal date.
- Historical source shape: `Nome do Perfume,Genero,Ml Disponiveis,Expired,Retirada`.

The corrected source must instead use:

```text
Nome do Perfume,Genero,Ml Disponiveis,Expired,Retirada,EssenceReference,ProductionTypeCode,LotCode
```

and must receive a new external SHA-256 after operator edits.

### Historical DRY_RUN — PASSED

A fresh disposable PostgreSQL 17 database contained zero public tables before normal Flyway startup. Two
consecutive runs against the same immutable historical source completed with the same normalized report fingerprint:

```text
totalRowCount=92
catalogOnlyCount=3
openingStockCount=89
rejectedCount=0
normalizedReportSha256=c0f021aa15ccfaf417d07ab481d5f1d64cf2e4922209bcec7480c76680ea491b
```

After `DRY_RUN`, database counts remained zero for catalog items, inventory batches, stock movements,
suppliers, and production executions. This proved that classification and reporting performed no business or
domain writes under the then-approved mapping.

All three nonblank `Retirada` values were checked locally with Java `BigDecimal`: each adjusted quantity
equalled `Ml Disponiveis + Retirada` exactly, none was negative, and the report used the adjusted opening
quantity. `Retirada` was treated only as an opening-balance adjustment; no historical movement was inferred.

The old `Scandall` name-disambiguation and essence-only interpretation are superseded by #231. Current
corrected identity uses explicit `ProductionTypeCode + EssenceReference`, persists `Genero` as product gender,
and preserves explicit source lot codes.

The dedicated historical `DRY_RUN` database/container was stopped and removed before `APPLY`.

### Historical disposable PostgreSQL APPLY — PASSED

A separate brand-new PostgreSQL 17 database contained zero public tables before startup. Flyway applied the
then-current migrations normally. `APPLY` used the same checksum-verified historical CSV and effective date as
`DRY_RUN` and completed without partial or rejected rows:

```text
totalRowCount=92
catalogOnlyCount=3
openingStockCount=89
rejectedCount=0
catalogItems=92
openingBatches=89
openingEntryMovements=89
```

Historical consistency checks found:

- zero negative batch balances;
- zero differences between adjusted initial quantity, current batch balance, and opening `ENTRY` quantity;
- exactly one batch and one `ENTRY` for every positive adjusted opening row;
- exactly three catalog-only items with no opening stock;
- zero orphan movements and zero non-`ENTRY` movements;
- zero non-null supplier IDs or lot codes on opening batches under the obsolete mapping;
- zero suppliers, production formulas, production executions, and production consumptions;
- every opening batch used the effective date `2026-09-06`.

The null-lot and `ESSENCE` conclusions above are **not** the corrected business contract. #231 requires
`FINISHED_PRODUCT`, persisted gender/reference/type metadata, and an explicit source `LotCode` for every
positive opening-stock row.

The disposable historical `APPLY` database/container was stopped and removed after evidence collection. No
row-level operational data is added to this report.

## Historical validation gates

- `backend/ ./mvnw verify`: **PASSED** — 398 tests, 0 failures, 0 errors, 0 skipped; build successful.
- Spring Modulith: **PASSED** — `ModularityTest` passed with no module-boundary violations.
- `frontend/ pnpm lint`: **PASSED** — all files pass linting.
- `frontend/ pnpm test`: **PASSED** — 47 test files and 231 tests passed.
- `frontend/ pnpm build`: **PASSED** — production bundle generated successfully.

Known non-blocking warnings from that run:

- Angular reported the initial bundle above the configured budget.
- A sandboxed Angular build could not resolve Google Fonts; the required build passed when network access was available.
- One frontend run exceeded an existing test timeout slightly; an immediate unchanged full-suite rerun passed.
- Maven logged existing non-blocking development/tooling warnings.

## Corrected cutover requirement

The corrected v0.6.1 cutover tracked by #233 must produce fresh evidence after #231 using the revised external
CSV and its new checksum. The operational sequence is documented in
[`initial-inventory-import.md`](initial-inventory-import.md): restore the known-good pre-import database,
verify the corrected checksum, run `DRY_RUN`, require `rejected=0` and operator review, run one atomic `APPLY`,
verify representative finished products/lots/balances/history, and create a verified post-import backup.
