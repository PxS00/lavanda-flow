#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)"
environment_file="${LAVANDA_OPERATIONAL_ENV_FILE:-$repository_root/.env.operational}"
output_directory="${LAVANDA_BACKUP_OUTPUT_DIR:-$repository_root/backups}"
project_name="${LAVANDA_OPERATIONAL_PROJECT_NAME:-lavanda-flow-operational}"

usage() {
  echo "Usage: $0 [--env-file PATH] [--output-dir PATH] [--project-name NAME]" >&2
}

while (($#)); do
  case "$1" in
    --env-file)
      environment_file="${2:?--env-file requires a path}"
      shift 2
      ;;
    --output-dir)
      output_directory="${2:?--output-dir requires a path}"
      shift 2
      ;;
    --project-name)
      project_name="${2:?--project-name requires a name}"
      shift 2
      ;;
    *)
      usage
      exit 2
      ;;
  esac
done

cd "$repository_root"

for command in docker sha256sum; do
  command -v "$command" >/dev/null || {
    echo "Required command not found: $command" >&2
    exit 1
  }
done

if [[ "$environment_file" != /* ]]; then
  environment_file="$repository_root/$environment_file"
fi
if [[ "$output_directory" != /* ]]; then
  output_directory="$repository_root/$output_directory"
fi

if [[ ! -f "$environment_file" ]]; then
  echo "Operational environment file not found: $environment_file" >&2
  exit 1
fi

umask 077
mkdir -p "$output_directory"

compose=(docker compose --project-name "$project_name" -f "$repository_root/compose.operational.yaml" --env-file "$environment_file")

if [[ -z "$("${compose[@]}" ps --status running -q postgres)" ]]; then
  echo "Operational PostgreSQL service is not running for project $project_name." >&2
  exit 1
fi

if ! "${compose[@]}" exec -T postgres sh -ceu 'exec pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' >/dev/null; then
  echo "Operational PostgreSQL service is not reachable." >&2
  exit 1
fi

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_name="lavanda-flow-$timestamp.dump"
backup_file="$output_directory/$backup_name"
checksum_file="$backup_file.sha256"

if [[ -e "$backup_file" || -e "$checksum_file" ]]; then
  echo "Backup artifact already exists: $backup_file" >&2
  exit 1
fi

partial_file="$(mktemp "$output_directory/.lavanda-flow-$timestamp.XXXXXX.partial")"
container_check_file="/tmp/lavanda-flow-backup-check.dump"
cleanup_partial() {
  rm -f "$partial_file"
  "${compose[@]}" exec -T postgres rm -f "$container_check_file" >/dev/null 2>&1 || true
}
trap cleanup_partial EXIT

"${compose[@]}" exec -T postgres sh -ceu '
  exec env PGPASSWORD="$POSTGRES_PASSWORD" pg_dump \
    --format=custom \
    --no-owner \
    --no-privileges \
    --username="$POSTGRES_USER" \
    --dbname="$POSTGRES_DB"
' > "$partial_file"

"${compose[@]}" cp "$partial_file" "postgres:$container_check_file"
"${compose[@]}" exec -T postgres sh -ceu "exec pg_restore --list $container_check_file" >/dev/null
"${compose[@]}" exec -T postgres rm -f "$container_check_file"

mv "$partial_file" "$backup_file"
trap - EXIT
(cd "$output_directory" && sha256sum "$backup_name" > "$(basename "$checksum_file")")

printf 'Backup created: %s\n' "$backup_file"
printf 'Checksum created: %s\n' "$checksum_file"
