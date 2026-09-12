# Initial inventory snapshot import

> **Corrected v0.6.1 cutover contract (#231).** The previous four/five-column perfume snapshot and its v0.6.0 checksum are historical evidence only and must not be reused for the corrected APPLY.

This is a one-time offline administration workflow. Keep the real operational CSV outside the repository. PostgreSQL becomes the operational source of truth after the accepted APPLY.

## Required source contract

The importer accepts exactly this UTF-8 header and order:

```text
Nome do Perfume,Genero,Ml Disponiveis,Expired,Retirada,EssenceReference,ProductionTypeCode,LotCode
```

The corrected source represents formulated bulk finished products, not raw essences.

Mapping:

- `category = FINISHED_PRODUCT`;
- `unitOfMeasure = MILLILITER`;
- `Genero` persists as product gender (`M`, `F`, `C`, `M/C`, `F/C`);
- `EssenceReference` is required and must be `001` through `999`;
- `ProductionTypeCode` is required and must be exactly three uppercase letters;
- `LotCode` is required for every row whose adjusted opening quantity is positive;
- `Retirada` preserves the #168 rule: `adjustedOpeningQuantity = Ml Disponiveis + Retirada` and never creates synthetic historical withdrawals;
- positive rows create one opening batch and exactly one `ENTRY` movement;
- zero adjusted stock creates catalog state only, with no batch or movement.

Rows sharing the same `ProductionTypeCode + EssenceReference` represent one imported bulk product and may create multiple opening batches when their positive rows use distinct source lot codes.

Do not fabricate supplier, purchase date, production execution, formula, genealogy, or placeholder lot data for pre-Lavanda-Flow stock.

## External checksum

After the operator finishes the corrected CSV, generate a **new** SHA-256 for that exact file. Do not reuse the historical v0.6.0 checksum.

Keep the approved value outside source control and supply it to the procedure below:

```powershell
$csv = (Resolve-Path -LiteralPath 'C:\controlled\inventory.csv').Path
$expectedSha256 = '<CORRECTED_CSV_SHA256>'
$effectiveDate = '2026-09-06'

if ((Get-FileHash -Algorithm SHA256 -LiteralPath $csv).Hash -ne $expectedSha256) {
    throw 'The corrected external CSV checksum does not match the approved file.'
}
```

Use the same explicit effective date for `DRY_RUN` and `APPLY`. If the operational correction selects a different approved effective date, record that decision with the cutover evidence; never backdate automatically to bypass expiration validation.

## Isolated validation

Validate the exact candidate/release image against a disposable PostgreSQL target before touching the operational database.

```powershell
$validationProject = "lavanda-flow-import-validation-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
$validationEnv = Join-Path $env:TEMP "$validationProject.env"

if (-not $validationProject.StartsWith('lavanda-flow-import-validation-')) {
    throw 'Refusing a non-disposable import-validation project name.'
}

@"
POSTGRES_DB=lavanda_import_validation
POSTGRES_USER=lavanda_import_validation
POSTGRES_PASSWORD=$([guid]::NewGuid().ToString('N'))
LAVANDA_SECURITY_BOOTSTRAP_ENABLED=false
"@ | Set-Content -Encoding ascii -NoNewline -LiteralPath $validationEnv

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

    # Continue only after rejected=0 and operator review of normalized
    # gender/reference/type/lot/quantity/expiration results.
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

The disposable validation project is the only place where this runbook uses `down -v`. Never run that cleanup command against the operational project.

## Corrected operational cutover

The mistaken v0.6.0 import must not be repaired with ad-hoc SQL or history deletion. Because it was detected before normal operator use, restore the verified known-good **pre-import** PostgreSQL backup first.

Required sequence:

1. deploy the selected v0.6.1 candidate/release without deleting the PostgreSQL volume;
2. restore the verified pre-import database state according to the backup/restore runbook;
3. confirm the restored target catalog is empty and PostgreSQL is healthy;
4. verify the corrected CSV SHA-256;
5. run `DRY_RUN` against the restored target;
6. require `rejected=0` and manually review representative normalized identity, gender, lot, quantity, and expiration values;
7. verify the checksum again immediately before APPLY;
8. run one `APPLY`;
9. verify representative `FINISHED_PRODUCT` rows, source lot codes, balances, expiration, and opening `ENTRY` history;
10. create and verify a post-import backup and an off-notebook copy.

Do not stop/recreate PostgreSQL and do not run `docker compose down -v` against the operational runtime.

### DRY_RUN

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

### APPLY

```powershell
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $csv).Hash -ne $expectedSha256) {
    throw 'The corrected external CSV checksum does not match the approved file.'
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

`APPLY` validates the complete file, requires an empty catalog, and commits atomically. After acceptance, stop using the CSV operationally; PostgreSQL is authoritative.
