# Project Codex configuration

The shared implementation defaults live in `.codex/config.toml`: `gpt-6.1-sol` with `low` reasoning. Its `[agents]` table sets `default_subagent_model = "gpt-6-luna"` and `default_subagent_reasoning_effort = "high"` for focused investigation.

Standalone `.codex/agents/*.toml` files define reusable roles with `name`, `description`, and `developer_instructions`. The filename matches the role name. No role registration table is needed with the current format.

| Role | Model / reasoning | Responsibility |
| --- | --- | --- |
| `repository_scout` | Inherits subagent defaults: `gpt-6-luna` / `high` | Repository behavior, callers, contracts, and relevant documentation |
| `test_scout` | Inherits subagent defaults: `gpt-6-luna` / `high` | Tests, persistence, migrations, rollback/restore, and concurrency coverage |
| `architecture_reviewer` | Explicit `gpt-6.1-sol` / `medium` | Independent review of boundaries, transactions, locking, invariants, and issue scope |

All three roles declare `sandbox_mode = "read-only"` and prohibit state-changing commands and external writes through instructions. None declares `approval_policy`; omitted settings inherit from the parent. These declarations do not establish effective isolation. They report evidence to the parent agent, which owns implementation and validation. Repository policy stays in `AGENTS.md` and its documentation routes; role files describe only their focused responsibilities. Delegate a bounded question only after the runtime safety gate below passes.

## Configuration ownership and precedence

Version the project configuration and role files. Keep personal preferences, authentication, providers, MCP setup, and machine-specific settings outside the repository in the user's Codex configuration.

For trusted projects, precedence is: CLI flags and `--config` overrides; project `.codex/config.toml` files from root to current directory (closest wins); selected user profile; user config; cloud-managed defaults; system config; built-in defaults. Enforced requirements can constrain these settings. Untrusted projects skip project-scoped `.codex/` layers; trust is a local user decision, not a versioned project setting.

For subagent model and reasoning, an explicit spawn value overrides `[agents]` defaults, which override the parent's values. Explicit values in a custom role file take precedence over that resolution. The reviewer therefore sets both model and reasoning; scouts use the shared defaults. Other omitted role settings inherit from the parent. Multi-agent tools are enabled by default in the current runtime, so no feature flag is required here. The `name` field identifies a role; matching filenames are a convention, not the registration mechanism.

Codex reapplies the parent's live runtime sandbox and approval overrides when spawning a child, even when a role file declares different defaults. Select safe parent permissions before delegation, then inspect each child's recorded context. Project trust permits loading configuration; it does not attest isolation. `workspace-write` does not enforce instruction-based file ownership. A filesystem or command-network sandbox does not prevent external App/MCP writes.

## Compatibility and validation

Issue #268 recorded Codex CLI `0.160.1` on 2026-10-06. Issue [#270](https://github.com/PxS00/lavanda-flow/issues/270) inspected CLI and parent runtime `0.162.0` on 2026-10-09. The following official pages were fetched and read on that date:

- [Config Basics](https://developers.openai.com/codex/config-basic/) describes project layers, trust, and precedence.
- [Config Reference](https://developers.openai.com/codex/config-reference/) documents the root model/reasoning and `[agents]` default keys.
- [Subagents](https://developers.openai.com/codex/subagents/) documents standalone role files, read-only sandbox overrides, and per-role model/reasoning precedence.

Use `codex --version`, `codex --help`, and `codex debug models --bundled` to check the installed runtime and its model catalog. Both requested identifiers and their configured reasoning levels are advertised by this release. Catalog support does not guarantee availability for every account or provider.

The CLI has no dedicated `config validate` command. `codex --strict-config doctor --json` provides a diagnostic, but **is not a strict rejection gate in the tested release**. In an isolated fixture, an unknown root key (`issue_270_unsupported_probe`) produced `config.load.status = "warning"`, summary `config loaded`, and a startup warning that the key was ignored. The overall exit code was 1 both with and without that key because authentication and network checks failed. `codex --strict-config features list` rejects the command itself: strict mode is unsupported for `features`. Do not infer strict field rejection from the flag or overall exit code. Inspect startup warnings and validate TOML/required fields separately; strict inference-session loading remains untested here.

An isolated `CODEX_HOME` containing byte-for-byte copies of the project config and three role files returned `config.load.status = "ok"`, zero configured MCP servers, and no unsupported-field warning. This checks compatibility of those files as an isolated configuration, not active project-layer origins or child execution. No nested inference session or app-server was started. App-server `config/read` can be collected by an external operator to inspect effective layers; it was not invoked in this campaign.

## Issue #270 runtime evidence and safety decisions

Evidence is limited to the recorded sessions/accounts/environments. No provider-side served-model telemetry was available. Bundled catalog entries are not account entitlement checks. The initial inspection and the subsequent successful independent CLI campaign are separate runs; the latter does not make the former safe.

### Initial parent inspection (delegation withheld)

The initial parent used a configured custom provider. Neither scout model was requested through inference in that attempt.

| Evidence | Observed value / result |
| --- | --- |
| Parent session ID | `01a120fe-d16d-7031-b02e-261265d2ccf5` |
| Parent turn ID | `01a120ff-5722-7c50-8843-0a053bf4e833` |
| `session_meta` | `cli_version = 0.162.0`, source `vscode`, originator `codex-tui` |
| `turn_context` model / effort | `gpt-6.1-sol` / `medium`; project root default remains `low` |
| `turn_context` policies | `workspace-write`, network access false, `approval_policy = on-request` |
| Live permission instructions | Workspace and `/tmp` writable; approval reviewer `auto_review`. Reviewer selection is separate from recorded approval policy. |
| Local trust | Original project entry is `trusted`; no trust entry was changed |
| Local user defaults inspected | Model `gpt-6.1-sol`, reasoning `medium`; no credentials or provider settings were printed |
| Project-layer origins | NOT TESTED: trust is observed, but no effective layer response was collected |
| Bundled model catalog | `gpt-6.1-sol` supports `low` and `medium`; `gpt-6-luna` supports `high` |
| Native role catalog | All three names/descriptions are exposed in `collaboration.spawn_agent`; reviewer advertises fixed `gpt-6.1-sol/medium`. Scout runtime model/effort were NOT TESTED in this attempt. |
| Parent tool catalog | External mutators exposed; child catalog cannot be inspected before spawning through the available API |

Selected tool names from the runtime catalog, collected without invoking them:

- `mcp__codex_apps__github_update_file` and `mcp__codex_apps__github_merge_pull_request`.
- `mcp__codex_apps__google_drive_batch_update_document`.
- `mcp__codex_apps__figma_use_figma`.
- `mcp__codex_apps__supabase_execute_sql` (arbitrary SQL can mutate database state).

**High severity governance risk:** this parent was unsuitable for reader validation. The spawn API exposed no sandbox/approval/tool-denial arguments, and no child isolation guarantee had been inspected. Delegation was withheld, without external writes or filesystem probes. Parent exposure remains FAIL for that setup; child exposure and enforcement were BLOCKED in that attempt, not established vulnerabilities in a spawned child. Official documentation confirms live parent overrides are reapplied, but no overridden child policy was reproduced in Lavanda Flow. The successful isolated campaign below resolves the smoke-test blockers, not this unsafe-parent condition.

**Medium severity validation risk:** treating `--strict-config doctor` as field rejection can accept ignored settings. Reproduction above warrants a documentation correction, not a model or role configuration change. Future validation must reject startup warnings explicitly and collect strict session-loading evidence externally.

### Historical intermediate safety gates

The operator reported these independent attempts before the successful campaign. Each exposed capability caused validation to stop; no mutating probe was performed. These are historical observations, not evidence that the final isolated session was unsafe.

| Session ID | Observed gate | Outcome |
| --- | --- | --- |
| `01a12139-e10d-7c32-8de8-2ec963dafe4c` | Worktree creation MCP remained exposed | FAIL; validation stopped |
| `01a12153-d9a5-75f3-93ef-5ce4ef3af0a1` | Image-generation capability remained exposed | FAIL; validation stopped |
| `01a12156-4af8-7fd2-9aed-8e180381cbd0` | Web capability remained exposed | FAIL; validation stopped |
| `01a1215f-e2e2-7b52-b829-7a6279d27773` | Final inventory satisfied the safety gate | PASS; all three native smoke tests passed |

### Successful independent CLI campaign

Parent session: `01a1215f-e2e2-7b52-b829-7a6279d27773`. Its `session_meta` records CLI `0.162.0`, source `cli`, and originator `codex-tui`. Its `turn_context` records `gpt-6.1-sol/low`, `read-only/never`. The permission profile records restricted filesystem access with a root read entry and restricted network. The working copy was `/tmp/lavanda-codex-270.1B1Nvn/lavanda-flow`, with isolated `CODEX_HOME` at `/tmp/lavanda-codex-270.1B1Nvn/codex-home`.

The operator supplied verified results, and consolidation inspected the existing parent/child session records in that isolated home without repeating inference. Each child's own `session_meta` names the exact role and the parent ID above. Child histories also contain inherited parent metadata/context: those inherited entries must not be mistaken for child settings. Child-specific `turn_context` and recorded tool inventories agree with the following matrix.

| Exact native role | Child session ID | Recorded model / effort | Child sandbox / approval / network | Tools | Result |
| --- | --- | --- | --- | --- | --- |
| `repository_scout` | `01a12160-5d99-71b0-81e9-31093e0da5c9` | `gpt-6-luna/high` | `read-only/never`, restricted | Common inventory below; no external mutators | PASS |
| `test_scout` | `01a12160-ab81-7ed1-9424-3a064435fc70` | `gpt-6-luna/high` | `read-only/never`, restricted | Common inventory below; no external mutators | PASS |
| `architecture_reviewer` | `01a12161-0725-7cb1-87b9-087815e73196` | `gpt-6.1-sol/medium` | `read-only/never`, restricted | Common inventory below; no external mutators | PASS |

All four recorded nested inventories contain the same 11 tool names: `apply_patch`, `clock__curr_time`, `create_goal`, `exec_command`, `get_goal`, `list_mcp_resource_templates`, `list_mcp_resources`, `read_mcp_resource`, `update_goal`, `view_image`, and `write_stdin`. Native orchestration, execution/wait, clock sleep and user-input tools were also exposed. No Apps, plugin mutators, external MCP mutators, web tools, image-generation tools or dedicated worktree tools were exposed. Generic MCP resource readers in the inventory do not imply external mutator access.

Filesystem mutation tools remained listed under recorded read-only policies. No deliberate write-denial probes were performed. The campaign establishes native invocation, runtime policy/context, restricted tool exposure and unchanged files during the observed reads; it does not independently prove every enforcement path rejects writes. Instructions prohibited edits, builds, tests, migrations and external writes; actual source-read calls were recorded. No tests, builds or database operations were executed, so application behavior was assessed statically.

### Bounded task results and inspected sources

Java source paths below are relative to `backend/src/main/java/com/ceudelavanda/lavandaflow/`; test paths are relative to `backend/src/test/java/com/ceudelavanda/lavandaflow/`.

| Role | Recorded task and source evidence | Findings |
| --- | --- | --- |
| `repository_scout` | Traced `inventory/infrastructure/web/FefoWithdrawalController.java`, `inventory/application/fefo/RegisterFefoWithdrawal.java`, `inventory/domain/FefoAllocationPolicy.java`, request/response DTOs and `catalog/InventoryItemOperationLock.java`; consulted `docs/inventory/fefo-allocation.md` and area instructions. | Inventory owns withdrawal/allocation; catalog exposes the public item lock. Allocation precedes mutation in a transaction, eligibility uses the injected `Clock`, and allocations create auditable consumption movements. Documentation drift noted below. |
| `test_scout` | Inspected tests `inventory/application/RegisterFefoWithdrawalIntegrationTest.java` and `inventory/application/StockReceiptFefoConcurrencyIntegrationTest.java`; item/batch lock adapters, withdrawal/receipt paths, and migrations `backend/src/main/resources/db/migration/V5__create_inventory_batch.sql`, `backend/src/main/resources/db/migration/V6__create_stock_movement.sql`, and `backend/src/main/resources/db/migration/V19__confirm_sales_with_fefo_audit.sql`. | Existing balance rollback and receipt/FEFO concurrency coverage. One rollback test mocks movement persistence, so rollback of persisted movements remains unverified. Recommended validation belongs to the parent implementation workflow, not a reader session. |
| `architecture_reviewer` | Independently inspected the FEFO path, `catalog/infrastructure/persistence/JpaInventoryItemOperationLock.java`, `inventory/infrastructure/persistence/SpringDataBatchRepository.java`, batch/movement domain types and persistence adapters, `backend/AGENTS.md`, inventory package structure and ADR 0008. | No actionable architectural findings in the bounded path. Public catalog lock API, item-before-batch locking, transactional atomicity and quantity invariants are consistent in the inspected sources. Runtime database behavior was not exercised. |

The bounded architecture smoke review is PASS and is separate from review of the documentation diff. The operator subsequently reported a final independent documentation review with CHANGES REQUIRED: one medium-severity finding identified missing worktree, image-generation and web controls in the reproducible procedure. The correction below incorporates those controls. The review session ID was not supplied; reviewer confirmation of this correction remains pending. No new review or smoke session was launched to address the finding.

## Independent operator procedure (outside Codex only)

Never run `codex exec` or start another Codex app-server from an active agent. The independent campaign above was launched externally and passed; no smoke tests were repeated during consolidation. The reusable procedure below was prepared for an external terminal/operator. Command options were checked against `0.162.0 --help`, `exec --help`, `doctor --help`, and a diagnostic invocation using explicit safety flags. Recheck help after upgrades.

The operator recovered shell-history entry 2188, the final recorded invocation, consistent with the successful campaign:

```bash
codex --strict-config \
  --sandbox read-only \
  --ask-for-approval never \
  --disable worktrees \
  --disable image_generation \
  -c 'web_search="disabled"' \
  -c 'features.apps=false' \
  -c 'features.plugins=false' \
  -c 'features.remote_plugin=false' \
  -c 'apps._default.enabled=false'
```

This is operator-supplied shell-history evidence, not an independently verified association between entry 2188 and session `01a1215f-e2e2-7b52-b829-7a6279d27773`. Session metadata, child contexts and inventories independently establish the recorded runtime values and exposed tools; they do not establish which historical switches caused those values. Effective configuration-layer provenance remains unverified.

The recovered command has no working-directory argument or `CODEX_HOME` assignment. It inherits the operator's current directory and environment. Before invoking it, the operator must enter the disposable repository and set `CODEX_HOME` to the isolated, approved validation home; verify both paths and keep personal/global configuration and credentials out of the fixture. The recorded campaign paths above come from runtime evidence and session storage, not this shell-history entry. The setup below is a reproducible example, not a reconstruction of unrecorded shell setup.

Use a disposable machine/container without production credentials, databases, mounts, personal MCP configuration, or external integrations. Restrict its filesystem independently when available. A disposable copy alone cannot stop external writes or reads of other machine files. Do not alter original authentication, global configuration, permissions, or project trust to make validation succeed.

The example creates an empty isolated home and copies the versioned configuration as an explicit user-layer fixture. It deliberately does not create trust entries. Project-layer loading/precedence must be checked separately in an already approved trusted environment; this fixture does not prove that layer is active. Authentication in the empty home may be unavailable: mark inference BLOCKED rather than copying credentials or changing the original account. An operator may use an independently authorized isolated environment already provisioned outside this task.

Run from the original repository in an external Bash terminal:

```bash
umask 077
validation_root="$(mktemp -d /tmp/lavanda-270.XXXXXX)"
rtk mkdir -p "$validation_root/repo" "$validation_root/codex-home/agents"
rtk git archive HEAD > "$validation_root/source.tar"
rtk tar -xf "$validation_root/source.tar" -C "$validation_root/repo"
# Include the reviewable, uncommitted documentation update in the fixture.
rtk cp docs/development/codex-subagents.md "$validation_root/repo/docs/development/codex-subagents.md"
rtk cp .codex/config.toml "$validation_root/codex-home/config.toml"
rtk cp .codex/agents/*.toml "$validation_root/codex-home/agents/"
rtk git -C "$validation_root/repo" init --quiet
cd "$validation_root/repo"
export CODEX_HOME="$validation_root/codex-home"
```

Before running inference, hash every copied file (excluding `.git`) into a manifest outside the copy, and capture original branch, HEAD, status, tracked-file SHA-256 hashes and untracked paths. Compare both manifests afterwards. No fixture commit is needed. The fixture contains tracked files only; do not copy `.env`, authentication, global config, or ignored local data.

Define the same explicit safety options for diagnostics and inference:

```bash
safety_options=(
  --strict-config --sandbox read-only --ask-for-approval never
  --disable worktrees --disable image_generation
  -c 'web_search="disabled"'
  -c 'features.apps=false' -c 'features.plugins=false'
  -c 'features.remote_plugin=false' -c 'apps._default.enabled=false'
  --disable hooks --disable skill_mcp_dependency_install
)
CODEX_HOME="$validation_root/codex-home" rtk proxy codex --version
CODEX_HOME="$validation_root/codex-home" rtk proxy codex --help
CODEX_HOME="$validation_root/codex-home" rtk proxy codex exec --help
CODEX_HOME="$validation_root/codex-home" rtk proxy codex debug models --bundled
CODEX_HOME="$validation_root/codex-home" rtk proxy codex \
  "${safety_options[@]}" -C "$validation_root/repo" doctor --json \
  > "$validation_root/doctor.json"
```

The reusable options include every recovered control. Disabling hooks and automatic skill MCP dependency installation preserves additional prepared-procedure safeguards; those two options were not in entry 2188. Disabling worktrees, image generation and web search is necessary to address the intermediate gate failures; inspect the final inventory rather than treating flags alone as proof of tool exclusion.

Inspect `config.load`, startup warnings, policy and integration settings separately from doctor exit status. In the earlier isolated diagnostic, explicit flags loaded successfully, approval policy was `Never`, filesystem/network were restricted, and all five then-tested disabled feature overrides were recorded. That diagnostic preceded the added worktree/image/web controls; it is not evidence that the full corrected recipe was executed. This was parsed diagnostic configuration, not proof of a child sandbox. Codex warned that helper aliases cannot be created under `/tmp`; helper readiness in that diagnostic was not verified. The later successful CLI campaign demonstrates inference readiness in its own environment. Neither observation reproduces the CONSTRÓI BRASIL HOME/symlink problem.

Empty home plus disabled Apps/plugins avoids importing personal integrations. Still inspect effective cloud/system layers and every tool catalog before proceeding. If any MCP server is inherited, disable each observed server with `-c 'mcp_servers.<actual-id>.enabled=false'` and verify it is absent; an empty local table does not necessarily clear lower layers. Stop if any external mutator remains available or an enforced policy conflicts. `never` denies new command approval; it does not independently filter external tools. No credential copying, integration uninstall, or global configuration modification belongs in this procedure.

### Safety gate and bounded prompts

First perform only a context/tool inspection in the independent session. In the isolated copy, pass a prompt through stdin to the version-verified invocation:

```bash
CODEX_HOME="$validation_root/codex-home" rtk proxy codex \
  "${safety_options[@]}" -C "$validation_root/repo" exec --json - \
  > "$validation_root/events.jsonl" <<'PROMPT'
Inspect only your runtime context and available tool names. Do not spawn yet,
read secrets, edit files, run tests/builds/migrations, or invoke external tools.
Stop if effective permissions are not read-only/never or external mutators exist.
Report recorded model/effort, role discovery, and missing runtime evidence.
PROMPT
```

Textual self-reports do not pass the gate. The operator must inspect recorded `session_meta`, `turn_context`, effective layers and runtime tool inventory. If only a claimed catalog is available, mark the tool gate BLOCKED. Do not use `--ephemeral`, because session records are needed. Store raw records privately outside tracked content; they can contain sensitive prompts or environment details. Return only sanitized IDs, relevant fields, tool names and findings. JSON event output alone may not contain all context/tool evidence.

After parent policy and tool denial have been independently established, use an externally launched safe native session to delegate one bounded task per role, sequentially. Inspect each child's actual context before substantive work; if the runtime cannot support that gate, do not proceed. Native delegation must use the exact existing role names, without explicit model substitutions:

| Role | Bounded task and initial sources |
| --- | --- |
| `repository_scout` | Trace the FEFO withdrawal public contract starting at `backend/src/main/java/com/ceudelavanda/lavandaflow/inventory/infrastructure/web/FefoWithdrawalController.java` and `inventory/application/fefo/RegisterFefoWithdrawal.java` under the same Java root. Read only relevant sections of `docs/inventory/fefo-allocation.md`. Return owners/modules, paths/symbols, contract evidence and unresolved questions. |
| `test_scout` | Inspect `backend/src/test/java/com/ceudelavanda/lavandaflow/inventory/application/RegisterFefoWithdrawalIntegrationTest.java` and `StockReceiptFefoConcurrencyIntegrationTest.java` in the same directory; follow relevant production callers and migrations under `backend/src/main/resources/db/migration/`. Return test names, transaction/rollback, locking and audit evidence, concrete gaps and recommended commands. Do not execute tests, builds or migrations. |
| `architecture_reviewer` | Independently inspect the same FEFO execution path against `backend/AGENTS.md`, relevant parts of `docs/architecture/inventory-package-structure.md` and the locking ADR `docs/architecture/decisions/0008-use-pessimistic-locking-for-stock-movements.md`. Review module APIs, transaction atomicity, locking and stock invariants. Return severity and source paths, or explicitly no findings; do not implement fixes. |

Each task must apply root/area `AGENTS.md`, issue/spec precedence, and only relevant documentation routes. Do not broaden it into an application audit. The operator must retain parent/child IDs and relationship, runtime version, model/effort context, effective policies, exposed tools, task text, inspected paths/symbols, results and PASS/FAIL/BLOCKED. Missing values stay missing. Repeat final independent diff review with the existing `architecture_reviewer` only after the same safety gate passes; include the issue, actual diff, evidence claims and scope.

## Repository integrity and completed checks

Original branch: `chore/270/validate-codex-subagent-isolation`. Original HEAD: `6affcf3f82f9a3e97cc367e73086c1309ca88821`. Initial `git status --short` was empty; initial diff against local `develop` was empty. SHA-256 hashes were captured for all 961 tracked files before validation. Before editing documentation, all 961 matched. After the documentation update, the only authorized hash difference is this document; the remaining 960 tracked files match. HEAD and branch remain unchanged. No tracked configuration, role definition, instruction file, production code, migration, dependency or release/Git policy changed.

The successful independent campaign checked 961 tracked files. Its before/after ordered path/per-file-hash manifest SHA-256 was identical: `15a28d028cae559ece0b39f88c28a0b946a59d91b9f2d48639fd122e559db0a0`. Branch and HEAD matched the values above, and only the pre-existing modification to this document remained. No mutations, builds, migrations, commits, pushes or PR operations occurred. This digest belongs to that campaign snapshot, before the present consolidation edits; it is not the final digest of the updated document. Consolidation captured a fresh local baseline and verified that only this authorized document changed.

Temporary diagnostic fixtures, official-page caches and integrity manifests were kept under a private `/tmp/lavanda-270-*` directory. No raw session log or credential was copied into the repository. Personal/global configuration and trust were read selectively for non-secret evidence and were not modified. No WSL HOME/symlink failure was reproduced. The global-home alias warning reflects this session's filesystem restrictions; its root cause was not investigated further.

| Check | Command / method | Result |
| --- | --- | --- |
| CLI/options | `rtk proxy codex --version`, `--help`, `exec --help`, `doctor --help`, `debug models --help` | PASS, `0.162.0` |
| Catalog support | `rtk proxy codex debug models --bundled` | PASS for configured identifiers/efforts; campaign accepted those runtime values, without independent provider entitlement or served-model verification |
| Project TOML and roles | Python 3 `tomllib`; assert exactly three names, required nonempty strings, declared sandbox and expected model/effort fields | PASS |
| Configuration compatibility | Isolated byte-for-byte root/role copies; `codex --strict-config doctor --json` | PASS for `config.load = ok` with no unsupported warnings; overall diagnostic FAIL due to absent auth/network |
| Strict rejection negative control | Unknown key in temporary fixture only | FAIL as a rejection gate; warning/ignored key reproduced |
| Earlier safety option syntax | Isolated doctor before the worktree/image/web controls were added | PASS for then-tested parsing and overrides; not execution proof for the corrected recipe |
| Independent native smoke campaign | Existing parent/child `session_meta`, child-specific `turn_context`, inventories and actual source-read calls | PASS for all three roles; no repetition during consolidation |
| Independent campaign integrity | 961-file before/after manifest digest recorded above | PASS; pre-existing documentation modification preserved |
| Documentation quality | Required documentation existence and Markdown/YAML trailing-whitespace checks from `.github/workflows/repository-quality.yml` | PASS |
| Prepared operator command syntax | Extracted Bash blocks checked with `bash -n` | PASS for syntax; commands not executed during consolidation |
| Paths and scope | Documented repository paths checked; `rtk git diff develop --name-only`, `rtk git diff --check`, hash comparison and tracked/untracked status | PASS; only this document changed |
| Application validation | Backend/frontend builds, tests, migrations, operational restore tests | NOT TESTED; no affected application or operational behavior |
| Final independent documentation review | Operator-reported independent review | CHANGES REQUIRED: medium finding on omitted controls corrected here; reviewer confirmation pending, review session ID unavailable |

No project configuration remediation was applied: the independent campaign records the expected role/model/policy values, and no measured project configuration defect requires a change. The forward path is reviewer confirmation of the documentation correction, then user-authorized PR review/checks and merge. The command-to-session association and effective layer origins remain unverified, with the available evidence documented explicitly. Rollback of this documentation change is restoration of this document. Existing role/model defaults are preserved. No application data or mixed-version rollout is involved.

## Acceptance criteria and remaining work

These rows follow the issue's acceptance criteria in order. Issue #270 is **not complete**.

| # | Acceptance criterion | Status / evidence |
| --- | --- | --- |
| 1 | Three roles discovered and invoked natively | PASS: independent CLI campaign and parent-child metadata |
| 2 | Root/default/role model and reasoning runtime values | PASS: parent/child contexts match expected values; no served-model telemetry claim |
| 3 | Child policies verified read-only/never or blocker recorded | PASS for recorded policies in successful setup; deliberate denial probes NOT TESTED |
| 4 | No external mutators, or stop and document exposure | PASS: inspected independent parent/child inventories; initial unsafe setup remains separately documented |
| 5 | Bounded task output per role | PASS: recorded source reads and findings above; no tests/builds/migrations |
| 6 | Baseline/post-run integrity and no committed artifacts | PASS: authorized documentation-only difference; nothing committed |
| 7 | Trust/defaults/overrides/sandbox safeguards distinguished | PASS for evidence and documented limits; effective layer origins NOT TESTED |
| 8 | Versioned documentation, IDs, matrix, setup and limitations | PASS for documented evidence: runtime records, recovered entry 2188, corrected controls and required environment setup; command-to-session association remains unverified |
| 9 | Avoid unnecessary configuration changes | PASS: no configuration changes |
| 10 | Preserve instructions, architecture and inventory rules | PASS: instruction and production file hashes unchanged |
| 11 | Separate optional role scope without implementation | PASS: recommendations below |
| 12 | Diff/TOML/config/path/scope checks | PASS for checks described above; strict session loading remains NOT TESTED |
| 13 | No prohibited application/global/secret/policy changes | PASS |
| 14 | PR, required review/checks and squash merge to develop | PENDING: PR review, CI and squash merge remain outstanding; no commit/push/PR authorized |

Independent CLI inference and all three bounded native tasks are PASS. Recovered entry 2188 documents the final recorded invocation, consistent with the campaign, without proving its association with the successful session UUID. Effective layer origins, provider-side served-model telemetry and deliberate write-denial probes remain NOT TESTED. The independent documentation review's medium finding is corrected locally; reviewer confirmation is pending. Review, CI and squash merge for the eventual PR remain a later stage.

## Potential follow-ups outside issue #270

- Reconcile `docs/inventory/fefo-allocation.md`, which excludes locking/concurrency from its scope, with ADR 0008 and the current item/batch locking implementation. Record the intended documentation boundary before changing either source.
- Assess a persisted-movement rollback integration test: the inspected rollback scenario mocks movement persistence and therefore does not establish rollback of persisted movement records. This is a test-evidence gap, not a demonstrated stock/audit defect.

No inventory guide, ADR, production code or application test was changed for these follow-ups.

## Optional future roles (no implementation)

| Proposed role | Recommendation and separate scope |
| --- | --- |
| `backend_worker` | Safe reader validation now has a passing campaign; consider only when repeated implementation demand justifies a writer. Separate issue: bounded backend ownership through public module APIs, transaction/inventory invariants, backend verification, explicit writer permissions and parent-owned integration. File ownership remains a coordination rule, not filesystem isolation. |
| `frontend_worker` | Consider alongside the backend worker only for demonstrably independent Angular work. Separate issue: frontend ownership, pt-BR/accessibility and backend-authoritative rules, lint/test/build, writer permissions and cross-layer contract coordination. Avoid parallel ownership of shared contracts. |
| `bug_fixer` | Defer: overlaps the main implementer and potential area workers. Reproduction plus existing scouts and a single responsible implementer is sufficient; a generic writer complicates ownership. |
| `final_reviewer` | Defer: existing `architecture_reviewer` already covers independent diff/scope review. Consider separately only if repeated reviews demonstrate a distinct unmet responsibility; require independence from implementation and verified read-only/never with no mutators. |

No additional roles, model profile changes or external-project configuration were introduced. Writable workers would require separate permission and coordination validation; this reader campaign does not authorize them.
