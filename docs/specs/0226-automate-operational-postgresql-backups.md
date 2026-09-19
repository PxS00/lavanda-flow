# Issue #226 — Automate operational PostgreSQL backups

## Objective

Automate the routine PostgreSQL backup workflow on the supported Windows operator host without creating a second backup implementation, requiring normal operator terminal use, or weakening the recovery guarantees established by issue #184.

The solution remains local-first, provider-neutral, zero-recurring-cost, and maintainer-controlled. PostgreSQL remains the source of truth. scripts/operations/backup-postgres.sh remains the authoritative backup implementation. Windows Task Scheduler only orchestrates routine execution.

## Source of truth

Apply, in order:

1. GitHub issue #226;
2. AGENTS.md;
3. docs/operations/postgresql-backup-restore.md;
4. docs/operations/local-go-live-runbook.md;
5. docs/operations/windows-operator-workstation.md;
6. docs/operations/local-operational-runtime.md;
7. docs/specs/0184-establish-local-backup-restore-workflow.md;
8. scripts/operations/backup-postgres.sh;
9. scripts/operations/verify-postgres-restore.sh;
10. this specification for implementation details of #226.

Issue #226 remains authoritative for objective, acceptance criteria, constraints, and out-of-scope behavior.

## Branch

    chore/226/automate-operational-postgresql-backups

The branch starts from the current develop head after the preceding v0.7.0 feature work.

## Existing behavior to preserve

The existing backup/recovery contract already provides:

- PostgreSQL 17 custom-format logical dumps;
- pg_dump with custom format, no owner, and no privileges;
- dump creation through the private operational PostgreSQL container;
- no host PostgreSQL client requirement;
- structural validation with pg_restore --list;
- atomic finalization after temporary-file creation;
- SHA-256 sidecars;
- restrictive artifact permissions where supported;
- ignored repository-local backups/ storage;
- a guarded disposable restore-verification harness;
- explicit destructive-recovery warnings;
- private PostgreSQL networking with no published host port 5432.

Issue #226 must orchestrate this implementation rather than duplicate its PostgreSQL logic.

The current manual commands remain supported:

    scripts/operations/backup-postgres.sh
    scripts/operations/verify-postgres-restore.sh backups/<backup>.dump

Full disposable restore verification remains a maintainer/release/recovery operation and must not become a daily scheduled action.

## Architectural decision

### Windows Task Scheduler is orchestration only

Routine automation belongs to the supported Windows host boundary, not to Spring Boot, Angular, PostgreSQL, Docker Compose, or a new long-running service.

Use Windows Task Scheduler to launch a small repository-owned PowerShell orchestration script.

The orchestration script invokes the existing Bash backup script through the Git Bash installation already used by the validated workstation maintenance workflow.

Do not reimplement pg_dump, pg_restore --list, operational Compose lookup, PostgreSQL credential handling, or dump naming in PowerShell.

Conceptual flow:

    Windows Task Scheduler
            |
            v
    PowerShell scheduled-backup orchestrator
            |
            v
    Git Bash -> scripts/operations/backup-postgres.sh
            |
            +--> validated local .dump
            +--> local .dump.sha256
            |
            +--> optional external copy
            +--> copied checksum verification
            |
            +--> conservative local retention

This preserves one authoritative backup implementation while using Windows-native scheduling and file-copy behavior.

## Scheduled execution contract

### Default cadence

Install one daily task with a documented default local start time of 20:00.

The install command must allow the maintainer to choose another local time without editing repository files.

The task uses the Windows host local timezone. No application Clock is involved because this is OS scheduling, not a domain date rule.

Configure the task so a missed trigger can run when Windows next makes the task runnable, using StartWhenAvailable or equivalent. Do not require the notebook to wake from sleep solely for backup.

Run in the current operator/maintainer Windows user context without embedding a Windows password in repository files or task arguments. The supported workstation already depends on interactive Windows sign-in for Docker Desktop startup.

### Task identity and concurrency

Use one stable task name:

    Lavanda Flow - PostgreSQL Backup

Do not allow overlapping task instances.

Installation must be idempotent: reinstall/update replaces the existing task configuration rather than creating duplicates.

Removal deletes only the scheduled task. It must never delete backup artifacts, PostgreSQL data, Docker volumes, or operational configuration.

## Repository scripts

Prefer two focused PowerShell scripts:

    scripts/operations/run-scheduled-backup.ps1
    scripts/operations/manage-backup-task.ps1

Equivalent naming is acceptable only if the responsibilities remain explicit.

### run-scheduled-backup.ps1

Responsibilities:

1. resolve the repository root independently of the caller working directory;
2. locate Git Bash using documented candidates and allow an explicit maintainer override;
3. invoke scripts/operations/backup-postgres.sh;
4. propagate backup failure as a non-zero process result;
5. identify the dump and checksum produced by that successful invocation;
6. when an external destination is configured, copy both artifacts;
7. verify the copied dump against the copied SHA-256 sidecar;
8. apply conservative local retention only after every required step for the run succeeded;
9. write a maintainer-readable local diagnostic log containing safe status/error information only;
10. exit zero only when the complete configured routine run succeeds.

Never log:

- .env.operational contents;
- PostgreSQL credentials;
- operator credentials;
- database rows;
- secrets from Docker/container environment.

The existing safe output from backup-postgres.sh may be consumed to identify the created artifact paths. If path conversion between Git Bash and Windows is required, use Git for Windows tooling such as cygpath rather than introducing a second backup path contract.

### manage-backup-task.ps1

Provide reproducible maintainer actions for at least:

- install/update;
- remove;
- inspect/status, when practical.

Installation inputs should include:

- schedule time, defaulting to 20:00 local;
- optional external backup destination;
- optional Git Bash path override when automatic discovery is insufficient.

Machine-specific paths are supplied at installation/runtime and must not be committed to tracked defaults.

The task action points at the repository-owned scheduled-backup script from the operational checkout. Moving the operational checkout requires reinstalling/updating the task.

## External backup destination

External/off-notebook copy is optional at script level but required operationally whenever the production workstation has an approved configured destination.

The implementation remains provider-neutral.

Valid destinations may include:

- a directory synchronized by an existing cloud-drive client;
- removable media;
- a trusted secondary device location exposed as a filesystem path.

Do not add:

- Google Drive SDK/API integration;
- provider-specific authentication;
- a hard-coded drive-letter path;
- a hard-coded username;
- a tracked real production destination.

The real Céu de Lavanda installation may use a Google Drive-synchronized directory by supplying its Windows filesystem path during task installation.

### Copy integrity

For a configured external destination:

1. create the local backup successfully;
2. validate it through the existing backup script;
3. copy the .dump;
4. copy the matching .dump.sha256;
5. calculate and verify the copied dump hash against the copied sidecar;
6. fail non-zero if copy or verification fails.

Do not consider a copied backup successful merely because the file-copy operation returned success.

If external copy fails, preserve the valid local backup and do not perform retention pruning for that run.

## Local retention policy

Issue #184 intentionally used manual retention. Issue #226 may automate only the established local baseline.

Keep at least the seven most recent successful routine backup pairs locally:

    lavanda-flow-<timestamp>.dump
    lavanda-flow-<timestamp>.dump.sha256

Retention rules:

- operate only on the known Lavanda Flow backup filename contract;
- treat a dump and matching checksum as one backup pair;
- never delete the newest successful backup;
- never delete the only remaining successful backup;
- never treat partial files as successful backups;
- do not automatically delete unmatched/orphaned files;
- prune only after the current backup completed successfully;
- when external copy is configured, prune only after external checksum verification succeeds;
- retain at least seven successful local pairs;
- do not automate external/weekly retention in this issue.

Use the generated filename timestamp as the canonical ordering signal. Skip filenames outside the supported pattern rather than guessing.

Pre-upgrade backups remain an explicit release/runbook gate. The scheduled daily job does not replace the required pre-upgrade backup and verified off-notebook copy.

## Diagnostics

Routine failures must be diagnosable without database or application secrets.

Use a local ignored location under the operational backup boundary, for example:

    backups/logs/

A run log may contain:

- start/end timestamps;
- safe script/task status;
- created artifact filenames/paths;
- external copy status;
- retention actions;
- sanitized command failures.

The process exit code must remain meaningful so Windows Task Scheduler can report success or failure.

Do not introduce telemetry, email, WhatsApp, push notifications, or cloud monitoring.

## Documentation updates

Update the existing operational documentation rather than creating a parallel backup manual.

At minimum revise:

    docs/operations/postgresql-backup-restore.md
    docs/operations/local-go-live-runbook.md

Document:

- daily scheduled backup behavior;
- default 20:00 local schedule and how to choose another time;
- install/update/remove/status commands;
- external destination configuration;
- copied checksum verification;
- local retention behavior;
- local diagnostic location;
- pre-upgrade backups remain explicit;
- disposable restore verification is not run daily;
- recovery remains a maintainer procedure;
- moving the operational checkout requires updating/reinstalling the scheduled task.

Update docs/operations/windows-operator-workstation.md only when needed to record validated production-host task evidence. Never commit machine-specific external paths, usernames, or credentials.

## Testing and validation strategy

Do not add a PowerShell testing framework solely for this issue.

Keep script responsibilities separable enough to validate safely without a real production dump or destructive database changes.

### Existing backup regression

Validate that:

- manual scripts/operations/backup-postgres.sh still succeeds;
- custom-format dump and checksum are produced;
- structural validation still occurs;
- existing restore-verification workflow remains callable.

### Scheduled orchestrator

Cover or manually demonstrate in an isolated/non-production path:

- backup child-process failure propagates non-zero;
- successful local backup is recognized;
- external copy succeeds to a temporary directory;
- copied checksum verification succeeds;
- tampered copied dump/checksum produces failure;
- external-copy failure preserves the valid local backup;
- retention keeps the seven newest successful pairs;
- retention does nothing when fewer than eight valid pairs exist;
- unmatched/malformed files are not deleted;
- diagnostics contain no credentials.

### Task management

On Windows validate:

- install creates exactly one task with the documented name;
- reinstall/update is idempotent;
- the trigger uses the configured time;
- missed-run/start-when-available behavior is configured;
- concurrent instances are prevented;
- a manual task start executes the routine path;
- task result reflects orchestrator success/failure;
- removal deletes only the task.

### Repository validation

Run applicable repository checks plus:

    git diff --check
    git status --short

Do not weaken production scripts to make tests easier.

## Expected implementation surface

The smallest expected change set is approximately:

    scripts/operations/run-scheduled-backup.ps1
    scripts/operations/manage-backup-task.ps1
    docs/operations/postgresql-backup-restore.md
    docs/operations/local-go-live-runbook.md

Changes to scripts/operations/backup-postgres.sh are not expected unless a small additive compatibility hook is demonstrably required by the orchestration layer. Do not duplicate backup behavior to avoid a narrow integration adjustment.

Backend/frontend application code, Flyway migrations, Compose topology, API contracts, and dependencies are not expected to change.

## Explicit non-goals

Do not implement:

- application-level backup APIs/buttons;
- Spring scheduled jobs;
- a backup microservice/daemon;
- a cron container;
- PostgreSQL replication;
- WAL archiving or PITR;
- cloud/provider SDKs;
- Google Drive API authentication;
- automatic destructive restore;
- daily disposable full restore verification;
- public PostgreSQL exposure;
- arbitrary user-file backup;
- new application notifications;
- unrelated v0.7.0 release work.

## Security and safety invariants

- PostgreSQL port 5432 remains unpublished.
- PostgreSQL credentials remain in the existing ignored operational boundary.
- No credentials are included in scheduled-task arguments.
- No real dumps are committed.
- No real off-notebook path or user identity is committed.
- SHA-256 provides integrity, not encryption; existing data-sensitivity guidance remains valid.
- No automated command may run docker compose down -v against the operational project.
- Routine automation never mutates business data.
- A failed routine backup never deletes the previous known-good backup.

## Acceptance mapping

Implementation is complete when:

- routine backups execute without operator terminal interaction;
- Task Scheduler launches the repository-owned orchestration at the documented/configured time;
- the orchestrator reuses backup-postgres.sh;
- every successful run produces a validated custom-format dump and SHA-256 sidecar;
- configured external copies are checksum-verified;
- machine/provider-specific destinations remain outside source control;
- failures return non-zero and leave safe local diagnostics;
- local retention keeps at least seven successful pairs and cannot remove the only known-good backup;
- explicit pre-upgrade backup/recovery gates remain unchanged;
- disposable restore verification remains manual/release-oriented;
- PostgreSQL remains private;
- task installation/update/removal is reproducible;
- existing manual backup/restore commands continue to work;
- repository validation and git diff --check pass.

## Final implementation report

Before proposing commits or a PR, report:

1. files created/modified;
2. relevant implementation decisions;
3. tests or validation added/performed;
4. production code changed and why;
5. results of applicable script/repository checks;
6. acceptance criteria still not satisfied;
7. git diff --check result;
8. git status --short.

Do not commit, push, open a PR, or merge implementation work unless explicitly requested.
