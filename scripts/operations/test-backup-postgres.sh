#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)"
backup_script="$repository_root/scripts/operations/backup-postgres.sh"
docker_cli="$(command -v docker)"
test_directory="$(mktemp -d "${TMPDIR:-/tmp}/lavanda-flow-backup-test.XXXXXX")"
test_project="lavanda-flow-backup-test-$$"
fake_bin="$test_directory/bin"
mkdir -p "$fake_bin"

cleanup() {
  "$docker_cli" compose --project-name "$test_project" -f "$repository_root/compose.backup.yaml" --env-file "$environment_file" down >/dev/null 2>&1 || true
  rm -rf "$test_directory"
}
trap cleanup EXIT
trap 'echo "Backup contract failed at line $LINENO" >&2' ERR

cat > "$fake_bin/docker" <<'EOF'
#!/bin/bash
set -euo pipefail

printf '%s\n' "$*" >> "$FAKE_DOCKER_LOG"
if [[ "$1" == "compose" && "$*" == *"run --rm -T --no-deps postgres-tooling"* ]]; then
  printf 'fake custom-format dump\n'
fi
if [[ "$1" == "run" ]]; then
  if (($# != 6)) || [[ "$2" != "--rm" || "$3" != "-i" || "$4" != "postgres:17-alpine" || "$5" != "pg_restore" || "$6" != "--list" ]]; then
    echo 'pg_restore must read stdin without an archive filename.' >&2
    exit 1
  fi
  if [[ "$(cat)" != 'fake custom-format dump' ]]; then
    echo 'pg_restore did not receive the dump on stdin.' >&2
    exit 1
  fi
  printf 'pg_restore stdin validated\n' >> "$FAKE_DOCKER_LOG"
  if [[ "${FAKE_PG_RESTORE_FAIL:-false}" == true ]]; then
    exit 1
  fi
  printf 'fake archive listing\n'
fi
EOF
chmod +x "$fake_bin/docker"

ca_file="$test_directory/prod-ca.crt"
printf '%s\n' 'test CA certificate' > "$ca_file"
environment_file="$test_directory/.env.operational"
test_password='a$b#c=!value'
cat > "$environment_file" <<EOF
SPRING_DATASOURCE_URL=jdbc:postgresql://db.example.test:5432/postgres?sslmode=verify-full&sslrootcert=/run/secrets/lavanda-postgres-ca.crt
SPRING_DATASOURCE_USERNAME=postgres.test
SPRING_DATASOURCE_PASSWORD='$test_password'
LAVANDA_DB_CA_CERTIFICATE_PATH=$ca_file
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=2
EOF

"$docker_cli" compose -f "$repository_root/compose.operational.yaml" --env-file "$environment_file" config --format json > "$test_directory/operational-config.json"
"$docker_cli" compose -f "$repository_root/compose.backup.yaml" --env-file "$environment_file" config --format json > "$test_directory/backup-config.json"
jq -e --slurpfile backup "$test_directory/backup-config.json" '
  .services["lavanda-flow-app"].environment.SPRING_DATASOURCE_PASSWORD == $backup[0].services["postgres-tooling"].environment.PGPASSWORD
  and .services["lavanda-flow-app"].volumes[0].source == $backup[0].services["postgres-tooling"].volumes[0].source
  and .services["lavanda-flow-app"].volumes[0].target == $backup[0].services["postgres-tooling"].volumes[0].target
' "$test_directory/operational-config.json" >/dev/null
jq -e '.services["postgres-tooling"].volumes[0].target == "/run/secrets/lavanda-postgres-ca.crt"' "$test_directory/backup-config.json" >/dev/null
dump_command_filter='
  .services["postgres-tooling"].command
  | (if type == "array" then join(" ") else . end)
  | contains("exec pg_dump")
    and contains("--format=custom")
    and contains("--no-owner")
    and contains("--no-privileges")
    and contains("--schema=public")
    and contains("--dbname=")
'
jq -e "$dump_command_filter" "$test_directory/backup-config.json" >/dev/null
jq '.services["postgres-tooling"].command |= (if type == "array" then map(gsub("--schema=public"; "")) else gsub("--schema=public"; "") end)' \
  "$test_directory/backup-config.json" > "$test_directory/unscoped-backup-config.json"
if jq -e "$dump_command_filter" "$test_directory/unscoped-backup-config.json" >/dev/null; then
  echo 'FAIL: backup command contract accepted a dump without the public schema boundary.' >&2
  exit 1
fi
expected_hash="$(printf '%s' "$test_password" | sha256sum | cut -d ' ' -f 1)"
actual_hash="$("$docker_cli" compose --project-name "$test_project" -f "$repository_root/compose.backup.yaml" --env-file "$environment_file" run --rm -T --no-deps postgres-tooling 'test -r "$PGSSLROOTCERT"; printf %s "$PGPASSWORD" | sha256sum' | cut -d ' ' -f 1)"
[[ "$actual_hash" == "$expected_hash" ]]
invalid_environment_file="$test_directory/invalid.env"
sed 's/sslmode=verify-full/sslmode=require/' "$environment_file" > "$invalid_environment_file"
if "$docker_cli" compose --project-name "$test_project" -f "$repository_root/compose.backup.yaml" --env-file "$invalid_environment_file" run --rm -T --no-deps postgres-tooling > "$test_directory/invalid.out" 2>&1; then
  echo 'FAIL: backup accepted a non-verifying TLS mode.' >&2
  exit 1
fi
grep -Fq 'sslmode=verify-full' "$test_directory/invalid.out"
! grep -Fq -- "$test_password" "$test_directory/invalid.out"

output_directory="$test_directory/backups"
log_file="$test_directory/docker.log"
PATH="$fake_bin:$PATH" FAKE_DOCKER_LOG="$log_file" \
  "$BASH" "$backup_script" --env-file "$environment_file" --output-dir "$output_directory" > "$test_directory/success.out"

backup_file="$(find "$output_directory" -maxdepth 1 -name '*.dump' -print -quit)"
[[ -n "$backup_file" && -f "$backup_file.sha256" ]]
(cd "$(dirname "$backup_file")" && sha256sum -c "$(basename "$backup_file").sha256") >/dev/null
grep -Fq -- 'compose -f' "$log_file"
grep -Fq -- 'run --rm -T --no-deps postgres-tooling' "$log_file"
grep -Fxq 'pg_restore stdin validated' "$log_file"
! grep -Fq 'fake archive listing' "$test_directory/success.out"
! grep -Fq -- 'a$b#c=!value' "$log_file"

rejected_output_directory="$test_directory/rejected-backups"
if PATH="$fake_bin:$PATH" FAKE_DOCKER_LOG="$log_file" FAKE_PG_RESTORE_FAIL=true \
  "$BASH" "$backup_script" --env-file "$environment_file" --output-dir "$rejected_output_directory" > "$test_directory/rejected.out" 2>&1; then
  echo 'FAIL: backup published an archive after pg_restore validation failed.' >&2
  exit 1
fi
[[ -z "$(find "$rejected_output_directory" -type f -print -quit)" ]]

echo 'Backup contract tests passed.'
