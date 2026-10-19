# Supabase availability monitoring

## Purpose

The `Supabase Availability` workflow performs only `SELECT 1` against the managed operational database. It declares checks at 02:17, 10:29, and 18:43 UTC and manual dispatch on `main`. Both `schedule` and `workflow_dispatch` require the workflow to exist on the repository default branch, `main`; integration into `develop` alone does not activate it. Once activated, it reduces low-activity risk and exposes failures; it is not an uptime SLA, guaranteed prevention of Free Plan pauses, an application health check, or a replacement for [independent backups and recovery](postgresql-backup-restore.md).

See [spec #271](../specs/0271-monitor-supabase-availability.md) for architecture, failure classifications, security decisions, and acceptance dependencies.

[Issue #271](https://github.com/PxS00/lavanda-flow/issues/271) owns implementation acceptance: workflow, monitor, credential-free tests, documentation, static validation, CI/review and PR integration into `develop`. [Issue #273](https://github.com/PxS00/lavanda-flow/issues/273) owns operational activation after release: production provisioning, effective privileges, live endpoint/TLS verification, dispatch, notifications, first scheduled run and final operational acceptance. The provisioning and activation steps below are performed under #273, not as part of #271 implementation validation. No live activation or end-to-end evidence is claimed here.

## Provision the monitor identity

On the intended operational project, a trusted database administrator provisions a dedicated login as an **operational credential**, outside application schema migrations. Do not reuse `postgres`, application/Flyway credentials, `service_role`, or Supabase API keys. No business objects are created.

Using a trusted TLS-verified interactive `psql` session, create the role without embedding a password in SQL or command history:

```sql
CREATE ROLE lavanda_monitor LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE
  NOINHERIT NOREPLICATION NOBYPASSRLS CONNECTION LIMIT 1;
GRANT CONNECT ON DATABASE postgres TO lavanda_monitor;
ALTER ROLE lavanda_monitor SET default_transaction_read_only = on;
ALTER ROLE lavanda_monitor SET statement_timeout = '5s';
```

Then set a generated unique password with `\password lavanda_monitor`. If the role already exists, inspect it instead of recreating it. The configured database must be the intended operational database; adapt only the `CONNECT` target if its name differs.

Before storing credentials, audit role attributes, role memberships, object ownership, effective database/schema/table/sequence/function privileges and default grants. PostgreSQL grants to `PUBLIC` apply even to `NOINHERIT` roles. Verify no business-table reads, writes, schema creation, privileged `SECURITY DEFINER` function execution, or application-user impersonation is possible. `SELECT 1` needs no business-table/schema access. Read-only defaults can be changed by a login and do not replace permission restrictions. A role-specific `REVOKE` cannot remove privileges inherited from `PUBLIC`; do not blindly revoke shared production permissions to make this monitor pass. If this audit fails, keep activation blocked and request a focused maintainer decision. Do not perform synthetic writes against production to test privilege denial; inspect effective grants instead.

## Configure GitHub Actions

In repository **Settings -> Secrets and variables -> Actions**, provision these values manually without including values in issues, logs, screenshots, commands or committed files:

| Kind | Name | Value |
| --- | --- | --- |
| Variable | `SUPABASE_PROJECT_REF` | Exact intended Dashboard project reference |
| Variable | `SUPABASE_DB_HOST` | Dashboard Connect shared session-pooler hostname, normally `aws-<index>-sa-east-1.pooler.supabase.com` |
| Variable | `SUPABASE_DB_NAME` | Operational database name, normally `postgres` |
| Variable | `SUPABASE_DB_USER` | `lavanda_monitor.<project-ref>` for the pooler |
| Secret | `SUPABASE_DB_PASSWORD` | Dedicated role password |
| Secret | `SUPABASE_DB_CA` | Trusted PEM CA bundle appropriate for the selected endpoint, obtained from the project's connection/TLS configuration |
| Optional secret | `SUPABASE_STATUS_TOKEN` | Scoped PAT restricted to this one project and Projects read-only |

Use session mode on 5432 for IPv4-capable GitHub-hosted runners. Direct `db.<ref>.supabase.co` with username `lavanda_monitor` is supported only if reachability is proven from the runner. Port 6543/transaction pooling is not supported. Validate the actual pooler certificate chain and hostname with `verify-full`; do not copy direct-endpoint trust assumptions, switch to `require`, disable verification, or use a hostname override. The selected CA must verify the actual endpoint; certificate failure blocks activation.

For optional status classification, create a **scoped** token at Supabase's account access-token page, select **only the intended project**, **Projects read-only**, and a bounded expiry. Check the review screen and current endpoint-to-permission mapping for `GET /v1/projects/{ref}`. No other capability, resource or write access is needed. Do not use “Create legacy token” or the broad token produced by CLI browser login. If that scope cannot be provided safely on this account, leave the secret absent and inspect project status manually after a failure. Never broaden credentials to obtain classification. Record token owner, scope verification and expiry outside public logs; rotate before expiry and delete superseded tokens.

## Release and operational activation under #273

1. Confirm #271 has passed required implementation review and CI and has been squash-merged through a normal PR into `develop`, explicitly linking #273 as the operational activation gate.
2. Promote through the existing `develop -> release/vX.Y.Z -> main` process, using a regular merge into `main`. Confirm the reviewed workflow revision is present on `main`. Do not change the default branch or branch protection for monitoring.
3. Under #273, provision or confirm the dedicated identity and GitHub configuration using the sections above. Verify role isolation including effective `PUBLIC` grants, all configured values, CA trust, Actions enablement and optional token scope. Leave the optional integration disabled if safe scope cannot be verified.
4. In **Actions -> Supabase Availability -> Run workflow**, select `main`. Non-main manual jobs are skipped by design and do not establish readiness. Run only on trusted released code.
5. Verify a successful PostgreSQL query: the safe log must say `HEALTHY`. Record the Actions run URL, released revision, date, result, and selected endpoint mode without secret values. This is the runner network/TLS acceptance evidence.
6. Verify the failure, paused, restoring and unknown-state procedures to the extent safely possible using existing credential-free tests/diagnostics. Do not intentionally pause or restore production. If a real pause occurs, follow manual recovery below and verify the subsequent database readiness result; a resume acknowledgement is insufficient.
7. Verify notification configuration and delivery with a controlled failure using an **isolated test repository and synthetic/absent configuration**, never by pausing or modifying the real Supabase project. Verify the responsible recipient and record safe evidence or an explicit delivery-test limitation.
8. Observe and record the first real scheduled run from `main` and verify the three staggered daily cron entries remain configured. Record scheduling delays, dropped-run/inactivity risks and Free Plan limits. Confirm the maintainer can follow manual resume, readiness recheck and possible local Spring Boot restart instructions.

Track completion against the acceptance checklist in #273. Record only sanitized run links, released revision, timestamps, statuses, endpoint mode, privilege/configuration audit outcomes and notification evidence or explicit test limitations. Never include secret values, raw provider/libpq diagnostics, connection strings or secret-bearing screenshots. Keep #273 open until its required evidence and any focused runbook corrections have been reviewed; report remaining provider/scheduler limitations. Closing #271 after implementation integration does not close this operational gate.

No real dispatch or endpoint validation is claimed by the branch-local mock tests.

## Status, notifications, and troubleshooting

Only `HEALTHY` succeeds. Known invalid credentials/TLS/configuration failures report `INVALID_CONFIGURATION`; fix the configuration or rotate the affected secret. Two failed queries followed by exact-project `ACTIVE_HEALTHY` report `TRANSIENT_CONNECTIVITY_FAILURE`; inspect network reachability, connection limits and pooler/provider incidents. `CONFIRMED_PAUSED` means verified `INACTIVE`. `RESTORATION_IN_PROGRESS` means verified `COMING_UP`/`RESTORING` and still fails until a database query succeeds. `UNKNOWN_PROVIDER_STATUS` means absent token, provider/API failure, unexpected response or unverifiable status; inspect Dashboard and [provider status](https://status.supabase.com/). A ninety-second process timeout or five-minute job timeout also fails visibly. Do not enable debug tracing or print raw provider/libpq errors to troubleshoot.

The maintainer must enable GitHub **Settings -> Notifications -> Actions** notifications, choose email/web delivery, and verify failure delivery for this workflow. Watching repository activity alone is not sufficient proof of delivery. GitHub's native notification routing can depend on who enabled or last edited a schedule; verify the responsible recipient rather than assuming every maintainer receives it. A failed run remains red in Actions even when notification delivery is unavailable. A missing/dropped scheduled run produces no failure notification, so check run freshness separately.

## Manual recovery

1. Open Supabase Dashboard through a trusted session. Verify organization, project name, reference and region against the intended `lavanda-flow` project before acting.
2. If paused, choose **Resume project** and confirm manually. Do not use a Management API write token in GitHub. An accepted request or `COMING_UP` is not readiness.
3. Wait for Dashboard restoration to finish. Do not repeatedly resume. If stuck or failed, inspect provider incidents and support guidance; never delete/recreate the project or restore a logical dump as an availability shortcut.
4. Dispatch the monitor on `main` again. Only its subsequent successful `SELECT 1` establishes monitored readiness. If it fails, investigate the classified state rather than assuming recovery.
5. If the local operator-hosted Spring Boot process did not recover after the prolonged outage, restart only `lavanda-flow-app` from the trusted operational checkout and verify normal access/health using the [go-live runbook](local-go-live-runbook.md). GitHub cannot restart that workstation.

Dashboard project resume and destructive logical database recovery are separate procedures. Existing backup and restore behavior remains unchanged.

## Maintenance and limitations

Free projects can pause despite these queries; Supabase does not guarantee immunity. GitHub schedules can be delayed or dropped under load, run from the default branch only, and are automatically disabled in a public repository after sixty days with no repository activity. Inspect Actions run freshness regularly, especially before an operating day and after extended inactivity; re-enable a disabled schedule in Actions and manually verify readiness. The workflow does not keep the operator notebook running and cannot detect a missing scheduled run by itself.

Maintain a named responsible operator and periodically review CA rotation, monitor-password validity, scoped-token expiry, role privileges/default grants after migrations, native notification delivery, runner `psql` availability, and current Supabase/GitHub lifecycle documentation. Do not introduce paid services or automatic restoration without a separately reviewed requirement. Monitoring is not a required PR check; the secret-free mock tests participate in the existing `repository-quality` gate.
