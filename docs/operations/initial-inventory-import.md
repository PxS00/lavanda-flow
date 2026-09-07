# Initial inventory snapshot import

This is a one-time offline administration workflow. Keep the operational CSV outside the repository and
use the same explicit effective date for both runs. The approved v0.5.0 cutover effective date is
`2026-09-06`.

The importer accepts only the original four-column header
`Nome do Perfume,Genero,Ml Disponiveis,Expired` or the final five-column header with trailing `Retirada`.
For the final shape, blank `Retirada` means zero; otherwise it is a negative milliliter adjustment, and the
opening quantity is `Ml Disponiveis + Retirada`. This derives the current opening snapshot only: it does not
create historical withdrawal or `CONSUMPTION` movements.

Before operational use, verify the SHA-256 of the frozen external CSV against the validated value in
[`v1-operational-readiness.md`](v1-operational-readiness.md):

```text
0d2c799d1efdb5d0924c36612b449ac6e42275da49f2d35c153e35165d2793b8
```

Do not copy the CSV into the repository or alter it after verification. From `backend/`, first validate the
complete file without database writes:

```bash
java -jar target/lavanda-flow-0.5.0.jar \
  --spring.main.web-application-type=none \
  --lavanda.inventory.initial-import.enabled=true \
  --lavanda.inventory.initial-import.mode=DRY_RUN \
  --lavanda.inventory.initial-import.file=/absolute/external/path/inventory.csv \
  --lavanda.inventory.initial-import.effective-date=2026-09-06
```

Inspect the ordered report and continue only when `rejected=0`; `DRY_RUN` must leave no business data
writes. Verify the frozen CSV SHA-256 again immediately before `APPLY`. Apply the same external file and
date only to an empty target accepted by the import guard:

```bash
java -jar target/lavanda-flow-0.5.0.jar \
  --spring.main.web-application-type=none \
  --lavanda.inventory.initial-import.enabled=true \
  --lavanda.inventory.initial-import.mode=APPLY \
  --lavanda.inventory.initial-import.file=/absolute/external/path/inventory.csv \
  --lavanda.inventory.initial-import.effective-date=2026-09-06
```

`APPLY` validates the complete file and commits atomically; do not bypass its empty-target guard. Then
verify catalog items, current stock, batches, and movement history in Lavanda Flow. After a successful
apply, PostgreSQL is authoritative; stop using the CSV operationally.
