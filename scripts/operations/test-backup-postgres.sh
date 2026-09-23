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
#!/usr/bin/env bash
set -euo pipefail

printf '%s\n' "$*" >> "$FAKE_DOCKER_LOG"
if [[ "$1" == "compose" && "$*" == *"run --rm -T --no-deps postgres-tooling"* ]]; then
  printf 'fake custom-format dump\n'
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
  bash "$backup_script" --env-file "$environment_file" --output-dir "$output_directory" > "$test_directory/success.out"

backup_file="$(find "$output_directory" -maxdepth 1 -name '*.dump' -print -quit)"
[[ -n "$backup_file" && -f "$backup_file.sha256" ]]
(cd "$(dirname "$backup_file")" && sha256sum -c "$(basename "$backup_file").sha256") >/dev/null
grep -Fq -- 'compose -f' "$log_file"
grep -Fq -- 'run --rm -T --no-deps postgres-tooling' "$log_file"
! grep -Fq -- 'a$b#c=!value' "$log_file"

echo 'Backup contract tests passed.'
