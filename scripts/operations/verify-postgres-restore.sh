#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)"

for command in docker curl openssl sha256sum; do
  command -v "$command" >/dev/null || {
    echo "Required command not found: $command" >&2
    exit 1
  }
done

usage() {
  echo "Usage: $0 [--strict-representative] BACKUP.dump" >&2
}

strict_representative=false
if (($# == 2)) && [[ "$1" == "--strict-representative" ]]; then
  strict_representative=true
  backup_file="$2"
elif (($# == 1)) && [[ "$1" != --* ]]; then
  backup_file="$1"
else
  usage
  exit 2
fi

if [[ ! -f "$backup_file" ]]; then
  echo "Backup artifact not found: $backup_file" >&2
  exit 1
fi
backup_file="$(cd -- "$(dirname -- "$backup_file")" && pwd -P)/$(basename -- "$backup_file")"

checksum_file="$backup_file.sha256"
if [[ -f "$checksum_file" ]]; then
  expected_checksum="$(awk '{print $1}' "$checksum_file")"
  actual_checksum="$(sha256sum "$backup_file" | awk '{print $1}')"
  if [[ ! "$expected_checksum" =~ ^[[:xdigit:]]{64}$ || "$expected_checksum" != "$actual_checksum" ]]; then
    echo "Backup checksum verification failed." >&2
    exit 1
  fi
  echo "Backup checksum verified."
fi

disposable_project="${LAVANDA_RESTORE_PROJECT_NAME:-lavanda-flow-restore-$(date -u +%Y%m%d%H%M%S)-$$}"
if [[ "$disposable_project" == "lavanda-flow-operational" || ! "$disposable_project" =~ ^lavanda-flow-restore-[a-z0-9_-]+$ ]]; then
  echo "Disposable restore project must start with lavanda-flow-restore- and cannot be lavanda-flow-operational." >&2
  exit 1
fi

http_port="${LAVANDA_RESTORE_HTTP_PORT:-18080}"
if [[ ! "$http_port" =~ ^[0-9]{2,5}$ ]]; then
  echo "LAVANDA_RESTORE_HTTP_PORT must be a valid TCP port." >&2
  exit 1
fi

umask 077
resources_owned=false
temporary_env="$(mktemp "${TMPDIR:-/tmp}/lavanda-flow-restore.XXXXXX.env")"
cleanup() {
  local status=$?
  set +e
  if [[ "$resources_owned" == true && "$disposable_project" == lavanda-flow-restore-* && "$disposable_project" != "lavanda-flow-operational" ]]; then
    "${compose[@]}" down -v --remove-orphans >/dev/null 2>&1
  elif [[ "$resources_owned" == true ]]; then
    echo "Refusing destructive cleanup for project $disposable_project." >&2
  fi
  rm -f "$temporary_env"
  return "$status"
}
trap cleanup EXIT

restore_password="$(openssl rand -hex 32)"
{
  printf '%s\n' 'POSTGRES_DB=lavanda_flow_restore'
  printf '%s\n' 'POSTGRES_USER=lavanda_restore'
  printf '%s\n' "POSTGRES_PASSWORD=$restore_password"
  printf '%s\n' "LAVANDA_HTTP_PORT=$http_port"
  printf '%s\n' 'LAVANDA_SECURITY_BOOTSTRAP_ENABLED=false'
} > "$temporary_env"

compose=(docker compose --project-name "$disposable_project" -f "$repository_root/compose.operational.yaml" --env-file "$temporary_env")
disposable_volume="${disposable_project}_postgres-data"

docker info >/dev/null
existing_containers="$(docker ps -aq --filter "label=com.docker.compose.project=$disposable_project")"
existing_networks="$(docker network ls -q --filter "label=com.docker.compose.project=$disposable_project")"
existing_volumes="$(docker volume ls -q --filter "label=com.docker.compose.project=$disposable_project")"
if [[ -n "$existing_containers" || -n "$existing_networks" || -n "$existing_volumes" ]] \
  || docker volume inspect "$disposable_volume" >/dev/null 2>&1; then
  echo "Disposable restore project or volume already exists; choose a new project name." >&2
  exit 1
fi

resources_owned=true
"${compose[@]}" up -d postgres
for _ in {1..30}; do
  if "${compose[@]}" exec -T postgres sh -ceu 'exec pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' >/dev/null; then
    break
  fi
  sleep 1
done

if ! "${compose[@]}" exec -T postgres sh -ceu 'exec pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' >/dev/null; then
  echo "Disposable PostgreSQL service did not become reachable." >&2
  exit 1
fi

"${compose[@]}" cp "$backup_file" postgres:/tmp/lavanda-flow-restore.dump
"${compose[@]}" exec -T postgres sh -ceu 'exec pg_restore --list /tmp/lavanda-flow-restore.dump' >/dev/null
"${compose[@]}" exec -T postgres sh -ceu '
  exec env PGPASSWORD="$POSTGRES_PASSWORD" pg_restore \
    --exit-on-error \
    --no-owner \
    --no-privileges \
    --username="$POSTGRES_USER" \
    --dbname="$POSTGRES_DB" \
    /tmp/lavanda-flow-restore.dump
'

query_count() {
  "${compose[@]}" exec -T postgres sh -ceu '
    exec env PGPASSWORD="$POSTGRES_PASSWORD" psql \
      --no-align \
      --tuples-only \
      --quiet \
      --set=ON_ERROR_STOP=1 \
      --username="$POSTGRES_USER" \
      --dbname="$POSTGRES_DB"
  '
}

assert_positive_count() {
  local label="$1"
  local count
  count="$(query_count <<< "$2")"
  if [[ ! "$count" =~ ^[1-9][0-9]*$ ]]; then
    echo "Restore verification failed: $label." >&2
    exit 1
  fi
  printf 'Verified %s: %s\n' "$label" "$count"
}

assert_zero_count() {
  local label="$1"
  local count
  count="$(query_count <<< "$2")"
  if [[ "$count" != "0" ]]; then
    echo "Restore verification failed: $label." >&2
    exit 1
  fi
  printf 'Verified no %s.\n' "$label"
}

flyway_failures="$(query_count <<< 'SELECT count(*) FROM flyway_schema_history WHERE success = false;')"
if [[ "$flyway_failures" != "0" ]]; then
  echo "Restore verification failed: Flyway history contains failed migrations." >&2
  exit 1
fi
assert_positive_count 'Flyway history entries' 'SELECT count(*) FROM flyway_schema_history;'
assert_zero_count 'batches without catalog items' '
  SELECT count(*)
  FROM inventory_batch batch
  LEFT JOIN inventory_item item ON item.id = batch.inventory_item_id
  WHERE item.id IS NULL;
'
assert_zero_count 'movements without batches' '
  SELECT count(*)
  FROM stock_movement movement
  LEFT JOIN inventory_batch batch ON batch.id = movement.batch_id
  WHERE batch.id IS NULL;
'
assert_zero_count 'formula ingredients with broken references' '
  SELECT count(*)
  FROM production_formula_ingredient ingredient
  LEFT JOIN production_formula formula ON formula.id = ingredient.formula_id
  LEFT JOIN inventory_item item ON item.id = ingredient.inventory_item_id
  WHERE formula.id IS NULL OR item.id IS NULL;
'
assert_zero_count 'production executions with broken output relationships' '
  SELECT count(*)
  FROM production_execution execution
  LEFT JOIN production_formula formula ON formula.id = execution.formula_id
  LEFT JOIN inventory_item item ON item.id = execution.output_inventory_item_id
  LEFT JOIN inventory_batch batch ON batch.id = execution.output_batch_id
  WHERE formula.id IS NULL
     OR item.id IS NULL
     OR batch.id IS NULL
     OR batch.inventory_item_id IS DISTINCT FROM execution.output_inventory_item_id
     OR batch.lot_code IS DISTINCT FROM execution.lot_code;
'
assert_zero_count 'production consumptions with broken source relationships' '
  SELECT count(*)
  FROM production_consumption consumption
  LEFT JOIN production_execution execution ON execution.id = consumption.execution_id
  LEFT JOIN inventory_batch source_batch ON source_batch.id = consumption.source_batch_id
  LEFT JOIN inventory_item source_item ON source_item.id = consumption.source_inventory_item_id
  LEFT JOIN stock_movement movement ON movement.id = consumption.movement_id
  WHERE execution.id IS NULL
     OR source_batch.id IS NULL
     OR source_item.id IS NULL
     OR movement.id IS NULL
     OR source_batch.inventory_item_id IS DISTINCT FROM consumption.source_inventory_item_id
     OR movement.batch_id IS DISTINCT FROM consumption.source_batch_id;
'
assert_zero_count 'broken production genealogy paths' '
  SELECT count(*)
  FROM production_consumption consumption
  LEFT JOIN inventory_batch source_batch ON source_batch.id = consumption.source_batch_id
  LEFT JOIN production_execution execution ON execution.id = consumption.execution_id
  LEFT JOIN inventory_batch output_batch ON output_batch.id = execution.output_batch_id
  WHERE source_batch.id IS NULL
     OR execution.id IS NULL
     OR output_batch.id IS NULL;
'

if [[ "$strict_representative" == true ]]; then
  assert_positive_count 'inventory items' 'SELECT count(*) FROM inventory_item;'
  assert_positive_count 'inventory batches' 'SELECT count(*) FROM inventory_batch;'
  assert_positive_count 'stock movements' 'SELECT count(*) FROM stock_movement;'
  assert_positive_count 'production formulas' 'SELECT count(*) FROM production_formula;'
  assert_positive_count 'production formula ingredients' 'SELECT count(*) FROM production_formula_ingredient;'
  assert_positive_count 'production executions' 'SELECT count(*) FROM production_execution;'
  assert_positive_count 'production consumptions' 'SELECT count(*) FROM production_consumption;'
  assert_positive_count 'batch to catalog-item relationships' '
    SELECT count(*)
    FROM inventory_batch batch
    JOIN inventory_item item ON item.id = batch.inventory_item_id;
  '
  assert_positive_count 'movement to batch relationships' '
    SELECT count(*)
    FROM stock_movement movement
    JOIN inventory_batch batch ON batch.id = movement.batch_id;
  '
  assert_positive_count 'formula ingredient relationships' '
    SELECT count(*)
    FROM production_formula_ingredient ingredient
    JOIN production_formula formula ON formula.id = ingredient.formula_id
    JOIN inventory_item item ON item.id = ingredient.inventory_item_id;
  '
  assert_positive_count 'production output relationships' '
    SELECT count(*)
    FROM production_execution execution
    JOIN production_formula formula ON formula.id = execution.formula_id
    JOIN inventory_item item ON item.id = execution.output_inventory_item_id
    JOIN inventory_batch batch ON batch.id = execution.output_batch_id
    WHERE batch.inventory_item_id = execution.output_inventory_item_id
      AND batch.lot_code = execution.lot_code;
  '
  assert_positive_count 'production consumption relationships' '
    SELECT count(*)
    FROM production_consumption consumption
    JOIN production_execution execution ON execution.id = consumption.execution_id
    JOIN inventory_batch source_batch ON source_batch.id = consumption.source_batch_id
    JOIN inventory_item source_item ON source_item.id = consumption.source_inventory_item_id
    JOIN stock_movement movement ON movement.id = consumption.movement_id
    WHERE source_batch.inventory_item_id = source_item.id
      AND movement.batch_id = source_batch.id;
  '
  assert_positive_count 'production genealogy paths' '
    SELECT count(*)
    FROM production_consumption consumption
    JOIN inventory_batch source_batch ON source_batch.id = consumption.source_batch_id
    JOIN production_execution execution ON execution.id = consumption.execution_id
    JOIN inventory_batch output_batch ON output_batch.id = execution.output_batch_id;
  '
fi

"${compose[@]}" up -d --build lavanda-flow-app
for _ in {1..60}; do
  if curl --fail --silent --show-error "http://127.0.0.1:$http_port/actuator/health" | grep -q '"status":"UP"'; then
    echo 'Restored application health check passed.'
    exit 0
  fi
  sleep 2
done

echo 'Restored application did not become healthy.' >&2
exit 1
