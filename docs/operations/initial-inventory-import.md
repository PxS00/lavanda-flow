# Initial inventory snapshot import

This is a one-time offline administration workflow. Keep the operational CSV outside the repository and
use the same explicit effective date for both runs.

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
