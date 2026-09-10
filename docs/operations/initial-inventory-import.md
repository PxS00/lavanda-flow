# Initial inventory snapshot import

This is a one-time offline administration workflow. Keep the operational CSV outside the repository and
use the same explicit effective date for both runs. The approved cutover effective date is `2026-09-06`.

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

Do not copy the CSV into the repository or alter it after verification. The importer runs from the current
operational Docker image; no host Java, Maven, or separately built JAR is required. Run these commands from
the selected candidate or release checkout after building that checkout's `lavanda-flow-app` image. The
read-only mount exposes the CSV only at `/import/inventory.csv`; it is neither copied into the image nor
written by the container.

## #187 isolated acceptance validation

Use this path only for acceptance validation. It creates a uniquely named disposable Compose project, private
PostgreSQL volume, network, and generated temporary credentials. It does not read `.env.operational`, join
`lavanda-flow-operational`, or touch its database or volume. The `finally` block removes only the generated
project and its disposable volume; do not change its project-name guard or use this cleanup command with the
operational project.

In PowerShell, set the external CSV path and create the isolated runtime:

```powershell
$csv = (Resolve-Path -LiteralPath 'C:\controlled\inventory.csv').Path
$expectedSha256 = '0d2c799d1efdb5d0924c36612b449ac6e42275da49f2d35c153e35165d2793b8'
$effectiveDate = '2026-09-06'
$validationProject = "lavanda-flow-import-validation-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
$validationEnv = Join-Path $env:TEMP "$validationProject.env"

if (-not $validationProject.StartsWith('lavanda-flow-import-validation-')) {
    throw 'Refusing a non-disposable import-validation project name.'
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $csv).Hash -ne $expectedSha256) {
    throw 'The external CSV checksum does not match the approved frozen file.'
}

@"
POSTGRES_DB=lavanda_import_validation
POSTGRES_USER=lavanda_import_validation
POSTGRES_PASSWORD=$([guid]::NewGuid().ToString('N'))
LAVANDA_SECURITY_BOOTSTRAP_ENABLED=false
"@ | Set-Content -Encoding ascii -NoNewline -LiteralPath $validationEnv
```

Run both modes against that disposable PostgreSQL target. `--no-deps` is intentional: PostgreSQL is started
and awaited explicitly, so the one-off container cannot create a second dependency stack. `docker compose run`
does not publish the service port unless `--service-ports` is supplied; do not supply it.

```powershell
try {
    docker compose -p $validationProject -f compose.operational.yaml --env-file $validationEnv build lavanda-flow-app
    docker compose -p $validationProject -f compose.operational.yaml --env-file $validationEnv up -d --wait postgres

    docker compose -p $validationProject -f compose.operational.yaml --env-file $validationEnv run --rm --no-deps `
      --volume "${csv}:/import/inventory.csv:ro" `
      lavanda-flow-app `
      --spring.main.web-application-type=none `
      --lavanda.inventory.initial-import.enabled=true `
      --lavanda.inventory.initial-import.mode=DRY_RUN `
      --lavanda.inventory.initial-import.file=/import/inventory.csv `
      --lavanda.inventory.initial-import.effective-date=$effectiveDate

    # Continue only after the DRY_RUN report has rejected=0.
    docker compose -p $validationProject -f compose.operational.yaml --env-file $validationEnv run --rm --no-deps `
      --volume "${csv}:/import/inventory.csv:ro" `
      lavanda-flow-app `
      --spring.main.web-application-type=none `
      --lavanda.inventory.initial-import.enabled=true `
      --lavanda.inventory.initial-import.mode=APPLY `
      --lavanda.inventory.initial-import.file=/import/inventory.csv `
      --lavanda.inventory.initial-import.effective-date=$effectiveDate
} finally {
    if ($validationProject -notlike 'lavanda-flow-import-validation-*') {
        throw 'Refusing cleanup outside the disposable import-validation project.'
    }
    docker compose -p $validationProject -f compose.operational.yaml --env-file $validationEnv down -v --remove-orphans
    Remove-Item -Force -LiteralPath $validationEnv -ErrorAction SilentlyContinue
}
```

Keep the accepted report as #187 evidence without recording CSV rows, generated credentials, or operational
data. The one-off application container exits and is removed by `--rm`; the `finally` block removes the
isolated PostgreSQL service, network, and volume.

## #188 real cutover

Use this path only after #188 has selected the exact release checkout and built its operational image as
described in the [local go-live runbook](local-go-live-runbook.md). It uses the existing healthy operational
PostgreSQL service on the `lavanda-flow-operational` network. Stop normal operator use for the migration
window, but do not stop or recreate PostgreSQL and do not use `down -v`.

In PowerShell, set and verify the same frozen source file and date:

```powershell
$csv = (Resolve-Path -LiteralPath 'C:\controlled\inventory.csv').Path
$expectedSha256 = '0d2c799d1efdb5d0924c36612b449ac6e42275da49f2d35c153e35165d2793b8'
$effectiveDate = '2026-09-06'

if ((Get-FileHash -Algorithm SHA256 -LiteralPath $csv).Hash -ne $expectedSha256) {
    throw 'The external CSV checksum does not match the approved frozen file.'
}
docker compose -f compose.operational.yaml --env-file .env.operational ps
```

Confirm `postgres` is healthy before starting the one-off container. First validate the complete file without
business-data writes:

```powershell
docker compose -f compose.operational.yaml --env-file .env.operational run --rm --no-deps `
  --volume "${csv}:/import/inventory.csv:ro" `
  lavanda-flow-app `
  --spring.main.web-application-type=none `
  --lavanda.inventory.initial-import.enabled=true `
  --lavanda.inventory.initial-import.mode=DRY_RUN `
  --lavanda.inventory.initial-import.file=/import/inventory.csv `
  --lavanda.inventory.initial-import.effective-date=$effectiveDate
```

Inspect the ordered report and continue only when `rejected=0`; `DRY_RUN` must leave no business data
writes. Verify the frozen CSV SHA-256 again immediately before `APPLY`. Apply the same external file and
date only to an empty target accepted by the import guard:

```powershell
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $csv).Hash -ne $expectedSha256) {
    throw 'The external CSV checksum does not match the approved frozen file.'
}

docker compose -f compose.operational.yaml --env-file .env.operational run --rm --no-deps `
  --volume "${csv}:/import/inventory.csv:ro" `
  lavanda-flow-app `
  --spring.main.web-application-type=none `
  --lavanda.inventory.initial-import.enabled=true `
  --lavanda.inventory.initial-import.mode=APPLY `
  --lavanda.inventory.initial-import.file=/import/inventory.csv `
  --lavanda.inventory.initial-import.effective-date=$effectiveDate
```

`APPLY` validates the complete file and commits atomically; do not bypass its empty-target guard. Then
verify catalog items, current stock, batches, and movement history in Lavanda Flow. After a successful
apply, PostgreSQL is authoritative; stop using the CSV operationally.
