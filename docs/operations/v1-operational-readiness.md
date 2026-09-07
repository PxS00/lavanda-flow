# V1 operational readiness

## Tested revision

- Branch: `test/138/validate-v1-operational-readiness`
- HEAD commit: `7c568ccb3ce31954984d33061199c959adabf0f8`
- Develop includes `79f4353d8a8034740b3390ea27119c7e293cdb49` (`#168`).
- Acceptance tests and this report were executed from the uncommitted issue #138 working tree on 2026-09-06.

## Automated acceptance

### Backend — PASSED

`V1OperationalReadinessAcceptanceTest` passed against PostgreSQL 17 through Testcontainers with a fixed
application clock. The scenario uses a synthetic #134-shaped CSV and proves:

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

## Real operational CSV — PASSED

- File: `inventory-snapshot-2026-09-06.csv`
- SHA-256: `0d2c799d1efdb5d0924c36612b449ac6e42275da49f2d35c153e35165d2793b8`
- Checksum verification: **PASSED** before `DRY_RUN`, immediately before `APPLY`, and after acceptance.
- Effective date: `2026-09-06`, the explicit date when Lavanda Flow establishes the opening snapshot; it is
  not a fabricated historical purchase, receipt, or withdrawal date.
- Approved source shape: `Nome do Perfume,Genero,Ml Disponiveis,Expired,Retirada`.

### DRY_RUN — PASSED

A fresh disposable PostgreSQL 17 database contained zero public tables before normal Flyway startup. Two
consecutive runs against the same immutable source completed with the same normalized report fingerprint:

```text
totalRowCount=92
catalogOnlyCount=3
openingStockCount=89
rejectedCount=0
normalizedReportSha256=c0f021aa15ccfaf417d07ab481d5f1d64cf2e4922209bcec7480c76680ea491b
```

After `DRY_RUN`, database counts remained zero for catalog items, inventory batches, stock movements,
suppliers, and production executions. This proves that classification and reporting performed no business or
domain writes.

All three nonblank `Retirada` values were checked locally with Java `BigDecimal`: each adjusted quantity
equalled `Ml Disponiveis + Retirada` exactly, none was negative, and the report used the adjusted opening
quantity. `Retirada` was treated only as an opening-balance adjustment; no historical movement was inferred.

Representative #134 normalization passed:

- `Scandall (M)` and `Scandall (F)` classified as distinct opening-stock catalog items;
- Aura Mugler's whitespace-bearing expiration normalized to `2027-09-30`;
- Hugo Boss Men normalized with blank expiration as catalog-only with zero opening quantity.

The dedicated `DRY_RUN` database/container was stopped and removed before `APPLY`.

### Disposable PostgreSQL APPLY — PASSED

A separate brand-new PostgreSQL 17 database contained zero public tables before startup. Flyway applied all
13 migrations normally. `APPLY` used the same checksum-verified CSV and effective date as `DRY_RUN` and
completed without partial or rejected rows:

```text
totalRowCount=92
catalogOnlyCount=3
openingStockCount=89
rejectedCount=0
catalogItems=92
openingBatches=89
openingEntryMovements=89
```

Post-import consistency checks found:

- zero negative batch balances;
- zero differences between adjusted initial quantity, current batch balance, and opening `ENTRY` quantity;
- exactly one batch and one `ENTRY` for every positive adjusted opening row;
- exactly three catalog-only items with no opening stock;
- zero orphan movements and zero non-`ENTRY` movements;
- zero non-null supplier IDs or lot codes on opening batches;
- zero suppliers, production formulas, production executions, and production consumptions;
- every opening batch used the effective date `2026-09-06`.

Representative public API reads from a normal application restart, with the importer disabled and no CSV
property configured, returned all 92 catalog items and the dashboard from PostgreSQL. They also returned both
exact Scandall names, Aura Mugler's normalized expiration, and Hugo Boss Men with quantity `0.000000`, zero
batches, and zero movements. This confirms normal operation depends only on PostgreSQL after import.

The disposable `APPLY` database/container was stopped and removed after evidence collection. The CSV and
checksum remained unchanged in the Git-ignored `operational-data.local` area; neither was copied, moved,
staged, or committed, and no row-level operational data was added to this report.

## Validation gates

- `backend/ ./mvnw verify`: **PASSED** — 398 tests, 0 failures, 0 errors, 0 skipped; build successful.
- Spring Modulith: **PASSED** — `ModularityTest` passed with no module-boundary violations.
- `frontend/ pnpm lint`: **PASSED** — all files pass linting.
- `frontend/ pnpm test`: **PASSED** — 47 test files and 231 tests passed.
- `frontend/ pnpm build`: **PASSED** — production bundle generated successfully.

Known non-blocking warnings:

- Angular reports the initial bundle at 667.75 kB, 167.75 kB above the configured 500 kB budget.
- A sandboxed Angular build could not resolve Google Fonts; the required build passed when network access was
  available.
- One frontend run exceeded the existing operational-workflow test's five-second timeout by 275 ms; an
  immediate unchanged full-suite rerun passed all 231 tests.
- Maven logs existing CycloneDX schema-keyword, Lombok/Unsafe deprecation, Mockito dynamic-agent,
  Testcontainers Docker-auth fallback, generated development security password, and enabled SpringDoc
  endpoint warnings.

## Blockers and remaining risks

- No release blocker was found.
- The real import was intentionally executed only in disposable acceptance databases. The production cutover
  still requires the documented backup, immutable-source checksum verification, `DRY_RUN`-before-`APPLY`,
  empty-target guard, operator review, and post-import verification procedure.
- Operational monitoring and backup/restore rehearsal remain deployment responsibilities outside this
  repository acceptance run.

## Conclusion

**READY** — automated backend/frontend composition, the checksum-verified frozen real-source `DRY_RUN`, the
fresh disposable PostgreSQL `APPLY`, persisted-state invariants, and PostgreSQL-only public reads all passed.
