#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)"
verifier="$repository_root/scripts/operations/verify-postgres-restore.sh"
test_directory="$(mktemp -d "${TMPDIR:-/tmp}/lavanda-flow-restore-test.XXXXXX")"
fake_bin="$test_directory/bin"
mkdir -p "$fake_bin"

cleanup() {
  rm -rf "$test_directory"
}
trap cleanup EXIT

cat > "$fake_bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

printf '%s\n' "$*" >> "$FAKE_DOCKER_LOG"

if [[ "$1" == "info" || "$1" == "ps" || "$1" == "network" ]]; then
  exit 0
fi
if [[ "$1" == "volume" ]]; then
  if [[ "$2" == "inspect" ]]; then
    exit 1
  fi
  exit 0
fi

arguments="$*"
if [[ "$arguments" == *"pg_isready"* || "$arguments" == *"pg_restore"* ]]; then
  exit 0
fi
if [[ "$arguments" != *"psql"* ]]; then
  exit 0
fi

query="$(cat)"
if [[ "$query" == *"success = false"* ]]; then
  [[ "${TEST_SCENARIO}" == "flyway-failure" ]] && printf '1\n' || printf '0\n'
  exit 0
fi
if [[ "$query" == *"LEFT JOIN"* ]]; then
  if [[ "${TEST_SCENARIO}" == "broken-relationship" && "$query" == *"FROM inventory_batch batch"* ]]; then
    printf '1\n'
  else
    printf '0\n'
  fi
  exit 0
fi
if [[ "$query" == *"flyway_schema_history"* ]]; then
  [[ "${TEST_SCENARIO}" == "flyway-empty" ]] && printf '0\n' || printf '14\n'
  exit 0
fi
if [[ "${TEST_SCENARIO}" == "representative" ]]; then
  printf '1\n'
  exit 0
fi
if [[ "${TEST_SCENARIO}" == "sparse" && "$query" =~ (inventory_item|inventory_batch|stock_movement) ]]; then
  printf '1\n'
  exit 0
fi
printf '0\n'
EOF

cat > "$fake_bin/curl" <<'EOF'
#!/usr/bin/env bash

if [[ "${TEST_SCENARIO}" == "health-failure" ]]; then
  exit 1
fi
printf '%s\n' '{"status":"UP"}'
EOF

cat > "$fake_bin/sleep" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$fake_bin/docker" "$fake_bin/curl" "$fake_bin/sleep"

backup_file="$test_directory/valid.dump"
: > "$backup_file"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

assert_contains() {
  local file="$1"
  local expected="$2"
  grep -Fq -- "$expected" "$file" || fail "expected $expected"
}

run_success() {
  local scenario="$1"
  shift
  local output="$test_directory/$scenario.out"
  local log="$test_directory/$scenario.log"

  : > "$log"
  if ! PATH="$fake_bin:$PATH" FAKE_DOCKER_LOG="$log" TEST_SCENARIO="$scenario" \
    LAVANDA_RESTORE_PROJECT_NAME="lavanda-flow-restore-test-$scenario-$$" \
    bash "$verifier" "$@" > "$output" 2>&1; then
    cat "$output" >&2
    fail "$scenario should succeed"
  fi
  assert_contains "$log" 'pg_restore --list'
  assert_contains "$log" 'down -v --remove-orphans'
  if grep -Fq -- '--project-name lavanda-flow-operational' "$log"; then
    fail "$scenario targeted the operational project"
  fi
}

run_failure() {
  local scenario="$1"
  local expected="$2"
  shift 2
  local output="$test_directory/$scenario.out"
  local log="$test_directory/$scenario.log"

  : > "$log"
  if PATH="$fake_bin:$PATH" FAKE_DOCKER_LOG="$log" TEST_SCENARIO="$scenario" \
    LAVANDA_RESTORE_PROJECT_NAME="lavanda-flow-restore-test-$scenario-$$" \
    bash "$verifier" "$@" > "$output" 2>&1; then
    fail "$scenario should fail"
  fi
  assert_contains "$output" "$expected"
}

run_usage_failure() {
  local output="$test_directory/usage.out"
  local log="$test_directory/usage.log"

  : > "$log"
  if PATH="$fake_bin:$PATH" FAKE_DOCKER_LOG="$log" TEST_SCENARIO=empty \
    bash "$verifier" "$@" > "$output" 2>&1; then
    fail "usage should reject $*"
  fi
  assert_contains "$output" 'Usage:'
  if [[ -s "$log" ]]; then
    fail "usage rejection invoked Docker"
  fi
}

run_success empty "$backup_file"
run_success sparse "$backup_file"
run_failure broken-relationship 'Restore verification failed: batches without catalog items.' "$backup_file"
run_success representative --strict-representative "$backup_file"
run_failure empty 'Restore verification failed: inventory items.' --strict-representative "$backup_file"
run_failure flyway-failure 'Restore verification failed: Flyway history contains failed migrations.' "$backup_file"
run_failure flyway-empty 'Restore verification failed: Flyway history entries.' "$backup_file"
run_failure health-failure 'Restored application did not become healthy.' "$backup_file"
run_usage_failure --strict-representative
run_usage_failure --unknown "$backup_file"

guard_output="$test_directory/guard.out"
guard_log="$test_directory/guard.log"
: > "$guard_log"
if PATH="$fake_bin:$PATH" FAKE_DOCKER_LOG="$guard_log" TEST_SCENARIO=empty \
  LAVANDA_RESTORE_PROJECT_NAME=lavanda-flow-operational \
  bash "$verifier" "$backup_file" > "$guard_output" 2>&1; then
  fail 'operational project name should be rejected'
fi
assert_contains "$guard_output" 'cannot be lavanda-flow-operational'
if [[ -s "$guard_log" ]]; then
  fail 'operational project-name rejection invoked Docker'
fi

echo 'Restore verification contract tests passed.'
