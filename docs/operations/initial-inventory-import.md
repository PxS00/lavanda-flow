# Initial inventory snapshot import

This is a one-time offline administration workflow. Keep the operational CSV outside the repository and
use the same explicit effective date for both runs.

The importer accepts only the original four-column header
`Nome do Perfume,Genero,Ml Disponiveis,Expired` or the final five-column header with trailing `Retirada`.
For the final shape, blank `Retirada` means zero; otherwise it is a negative milliliter adjustment, and the
opening quantity is `Ml Disponiveis + Retirada`. This derives the current opening snapshot only: it does not
create historical withdrawal or `CONSUMPTION` movements.

From `backend/`, first validate the complete file without database writes:

```bash
java -jar target/lavanda-flow-0.5.0-SNAPSHOT.jar \
  --spring.main.web-application-type=none \
  --lavanda.inventory.initial-import.enabled=true \
  --lavanda.inventory.initial-import.mode=DRY_RUN \
  --lavanda.inventory.initial-import.file=/absolute/external/path/inventory.csv \
  --lavanda.inventory.initial-import.effective-date=2026-09-05
```

Inspect the ordered report and continue only when `rejected=0`. Apply the same external file and date:

```bash
java -jar target/lavanda-flow-0.5.0-SNAPSHOT.jar \
  --spring.main.web-application-type=none \
  --lavanda.inventory.initial-import.enabled=true \
  --lavanda.inventory.initial-import.mode=APPLY \
  --lavanda.inventory.initial-import.file=/absolute/external/path/inventory.csv \
  --lavanda.inventory.initial-import.effective-date=2026-09-05
```

Then verify catalog items, current stock, batches, and movement history in Lavanda Flow. After a successful
apply, PostgreSQL is authoritative; stop using the CSV operationally.
