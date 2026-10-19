# Issue #271 — Supabase availability monitoring

## Source of truth and requirements

[Issue #271](https://github.com/PxS00/lavanda-flow/issues/271) owns implementation acceptance: the workflow, Python monitor, credential-free tests, documentation, static validation, required CI/review, and PR integration into `develop`. [Issue #273](https://github.com/PxS00/lavanda-flow/issues/273) owns post-release operational activation and its live evidence. The approved split preserves [ADR 0011](../architecture/decisions/0011-use-supabase-managed-operational-postgresql.md) and **manual recovery only**. The monitor does not resume, pause, delete, restart, or mutate a Supabase project. No application code, migrations, business reads/writes, paid service, or backup/restore behavior changes.

Normal checks execute only `SELECT 1`, three times daily: 02:17, 10:29, and 18:43 UTC (23:17 on the preceding day, 07:29, and 15:43 in America/Sao_Paulo at UTC-3). A successful normal execution makes one query; a failed attempt may retry once after five seconds. Availability requires exit status zero and exactly the scalar `1`. Every unresolved failure exits nonzero.

## Architecture and ownership

GitHub-hosted Ubuntu 24.04 runs the repository operational Python script with its existing Python standard library and native `psql`. These tools are available in the runner image; a missing client fails as invalid configuration. There are no new application dependencies. The operational workflow is independent of Spring Boot -> JDBC -> PostgreSQL. Spring Security, Flyway, all domain rules, and PostgreSQL source-of-truth ownership remain intact.

The responsible layer is `scripts/operations/` and `.github/workflows/`. The repository-quality workflow executes synthetic tests without any production credentials, including on PRs. The monitor itself has only `schedule` and `workflow_dispatch` triggers and accepts only `refs/heads/main`. It never runs PR/fork code with secrets. `GITHUB_TOKEN` has only `contents: read`; checkout does not persist it. Repository-wide concurrency prevents overlapping scheduled/manual jobs without cancelling an active check. The availability workflow is not a branch-protection gate.

## Credentials, permissions, and TLS

See the [operational runbook](../operations/supabase-availability.md) for provisioning and privilege verification. Variables hold project reference, endpoint, database, and username; secrets hold the separate monitor password, trusted CA PEM, and optional status token. No connection URL is accepted. The role is fixed to `lavanda_monitor`, or `lavanda_monitor.<ref>` for Supavisor. It must not own objects, inherit application roles, read business tables, mutate schemas, or exercise application authorization. Auditing inherited `PUBLIC` grants is an activation requirement; `default_transaction_read_only` alone is not a security boundary.

Use the Dashboard's IPv4-compatible shared **session pooler** hostname, port 5432, for standard GitHub-hosted runners. Direct `db.<ref>.supabase.co:5432` is allowed only after validating that runner's network reachability; do not assume IPv6 works. Transaction mode/6543 is not accepted. The script verifies hostname shape and direct-project reference, but the maintainer must verify the pooler hostname belongs to the intended project. Libpq always uses `sslmode=verify-full` with the approved CA and hostname; no insecure fallback. The CA is parsed before connecting, written within a private temporary directory with mode 0600, then removed. An isolated libpq environment ignores inherited service, TLS, host, and options overrides; `psql -X -w` avoids startup scripts and password prompts.

An optional Management API token is permitted only with **exactly the intended project** selected and **Projects read-only** capability, sufficient for `GET /v1/projects/{ref}` (`projects:read` / `project_admin_read` in the current API contract). Do not select an organization-wide resource, read-write capability, Database, API Key Secrets, or a legacy broad PAT. Scoped PATs became generally available on 2026-10-06; validate Dashboard availability, endpoint permission mapping, scope and expiry at activation. The monitor cannot introspect/enforce a provisioned token's scope: that is a maintainer provisioning responsibility. If safe restriction is unavailable, omit the token; a failed database check then reports unknown status and requires Dashboard inspection.

The API is consulted only after both database attempts fail. It uses verified HTTPS, a fixed provider origin, no redirects or environment proxy, a ten-second socket timeout, and at most 64 KiB of JSON. The returned `ref` must match the configured project; any returned legacy `id` must also match. Only the status is inspected in memory. No response, exception, connection string, credential, SQL output, or libpq diagnostic is printed, uploaded, or added to a job summary. Diagnostics are fixed strings. GitHub masking supplements this design rather than making unsafe logging acceptable.

## Failure classification and bounds

| Result | Meaning | Actions result |
| --- | --- | --- |
| `HEALTHY` | A bounded, verified PostgreSQL query returned `1` | Success |
| `TRANSIENT_CONNECTIVITY_FAILURE` | Query failed twice; exact project API reports `ACTIVE_HEALTHY`; investigate runner/network/pooler | Failure |
| `CONFIRMED_PAUSED` | Exact project API reports `INACTIVE` | Failure; manual Dashboard recovery |
| `RESTORATION_IN_PROGRESS` | Exact project API reports `COMING_UP` or `RESTORING` | Failure; wait and manually recheck |
| `UNKNOWN_PROVIDER_STATUS` | No token, API timeout/rate limit/server error, mismatched/malformed/oversized response, unsupported status, or unexpected internal error | Failure; inspect Dashboard/provider status |
| `INVALID_CONFIGURATION` | Missing/invalid input, CA, client, known authentication/TLS/permission/target error, unexpected query output, or API 401/403 | Failure; repair configuration |

`TRANSIENT_CONNECTIVITY_FAILURE` is a diagnostic hypothesis from a healthy provider status, not proof of a root cause. Without a safely provisioned token, project lifecycle distinctions cannot be established. Unknown states, including `PAUSING`, are never mapped to confirmed pause. The monitor does not claim recovery or readiness from API status or an acknowledgement.

Each query has a ten-second connect timeout, five-second server statement timeout, and fifteen-second client-process limit. There are at most two attempts and one five-second delay. The entire monitor command has a ninety-second process limit (also bounds slow API bodies); the job has a five-minute limit. No automatic restoration calls or polling loops exist.

## Recovery policy and security decisions

The maintainer verifies the intended Dashboard project, resumes it manually if paused, waits for restoration, and dispatches another monitor run on `main`. The successful subsequent database query is the readiness evidence. A prolonged outage may require restarting the local operator-hosted Spring Boot container; GitHub does not control that workstation. Provider resume is distinct from logical backup restoration; the existing backup/recovery policy is unchanged.

Automatic restoration would require project lifecycle write privileges and creates credential blast radius, accidental-target, repeated-call, and false-readiness risks. It is deliberately absent, with no enable flag or dormant write path. A future change requires separate maintainer review and bounded, exact-project, confirmed-paused gating plus subsequent database readiness verification. It must not reuse a broad token.

The least-privilege role audit must include PostgreSQL `PUBLIC` function, schema, and database privileges; per-role `REVOKE` does not override a `PUBLIC` grant. If existing shared privileges prevent safe isolation, stop activation and seek a separately reviewed privilege decision, rather than changing production grants or weakening this requirement. Certificate rotation, token expiration/revocation, supply-chain trust in runner/checkout, secret access by repository maintainers, and public safe diagnostic visibility remain operational risks.

## Validation and activation dependencies

Synthetic unit tests cover success/exact SQL and libpq contract, missing/invalid inputs, authentication/TLS errors, transient network failure, one successful retry, bounded repeated timeouts, paused/restoring states, unknown/mismatched/oversized API responses, HTTP failures, API-only classification, no redirects/proxies, temporary certificate cleanup, logging safety, and workflow credential boundaries. Tests mock all external connections. They do not pause, restore, delete, or modify the real project.

Local validation must include YAML parsing, Actionlint, Python compilation, shell syntax for workflow commands, the existing repository-quality documentation/whitespace checks and operational script tests. Backend/frontend suites are unrelated and are not run. Inspect tracked and new-file diffs and final status. Real identity privileges, runner TLS/network compatibility, native notifications and schedule execution require activation evidence, not mocks.

### Acceptance matrix

| Requirement | Acceptance owner | Required evidence |
| --- | --- | --- |
| Focused workflow with three staggered daily schedules, `workflow_dispatch`, bounded `SELECT 1`, verified-TLS configuration, safe failure classifications, credential boundaries and minimum GitHub permissions | #271 | Versioned workflow/monitor, credential-free tests and static checks |
| Runbook covering isolated credentials, effective grants including `PUBLIC`, endpoint/TLS configuration, notifications, manual recovery and provider/scheduler limits; unchanged application architecture and backup policy | #271 | Documentation alignment and complete tracked/untracked scope check |
| Required implementation review and CI, normal PR to `develop` and squash merge | #271 | Review, required check results and merged PR explicitly linking #273 |
| Workflow reaches default branch `main` through `develop -> release/vX.Y.Z -> main` | #273 | Released workflow revision through the existing release process |
| Production variables/secrets, dedicated role and effective-grant audit, CA trust and optional project-scoped read-only token verification | #273 | Sanitized provisioning and privilege/scope audit evidence; optional API integration stays disabled if safe scope is unavailable |
| Real runner endpoint connectivity and TLS server identity, successful `workflow_dispatch` on `main` | #273 | Safe Actions run link, released revision, timestamp and successful PostgreSQL readiness result |
| Safe failure/recovery procedure verification, actual notification configuration/delivery and first scheduled run | #273 | Sanitized operational evidence and explicit limits where verification cannot safely be completed |
| Final operational acceptance and any focused runbook corrections | #273 | Reviewed evidence against that issue's acceptance checklist and remaining provider/scheduler limitations |

Issue #271 can close after implementation review, required CI and normal squash integration into `develop`; no production credentials or live operations are required for its acceptance. This does not establish operational readiness. Both `schedule` and `workflow_dispatch` require the workflow to exist on the repository default branch, `main`. Activation follows the existing [release process](../development/git-workflow.md) without bypassing `develop -> release/vX.Y.Z -> main`. All outstanding operational requirements and completion evidence remain tracked in #273; no issue-closing automation or branch-protection changes are required.

Activation also depends on #254's managed-database operation, a dedicated audited role, valid CA/password, safely restricted optional token, enabled Actions, verified notifications, and a maintainer responsible for scheduler maintenance. Free Plan queries do not guarantee prevention of pauses or uptime. Public-repository schedules can be delayed/dropped and disabled after sixty days without repository activity.

Current evidence: local mock/static validation only. Real database/Management API credentials were not accessed, no production query was made, and no `workflow_dispatch` was performed. No live activation or end-to-end validation is claimed. Production activation remains pending release and maintainer provisioning under #273.

Local validation results (2026-10-09): 12 synthetic tests passed; Actionlint 1.7.12 passed for both changed workflows; all six workflow YAML files parsed and their shell commands passed `bash -n`; Python syntax passed; existing restore-verification tests passed; existing required-documentation and trailing-whitespace checks passed on clean-checkout contents; `git diff --check` passed. The literal whitespace command in the populated local workspace encountered ignored `frontend/node_modules` documentation; the same unchanged check passed on a temporary copy of tracked/new source files without installed dependencies. Existing backup contract validation is blocked because Docker Desktop WSL integration is unavailable (failure at its first Compose invocation, line 58). The Docker-backed operational image gate cannot be exercised in this environment. No backend/frontend source was changed or suites run.

## References checked for implementation

- [Supabase changelog](https://supabase.com/changelog.md) and [scoped PAT availability](https://supabase.com/changelog/scoped-personal-access-tokens-ga), checked 2026-10-09.
- [Connection modes](https://supabase.com/docs/guides/database/connecting-to-postgres), [project pausing](https://supabase.com/docs/guides/platform/free-project-pausing), and [Management API OpenAPI](https://api.supabase.com/api/v1-json), checked 2026-10-09.
- [GitHub workflow events](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows) and repository Git/branch-protection/issue-closing rules.
