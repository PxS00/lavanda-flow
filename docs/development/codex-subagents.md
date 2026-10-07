# Project Codex configuration

The shared implementation defaults live in `.codex/config.toml`: `gpt-6.1-sol` with `low` reasoning. Its `[agents]` table sets `default_subagent_model = "gpt-6-luna"` and `default_subagent_reasoning_effort = "high"` for focused investigation.

Standalone `.codex/agents/*.toml` files define reusable roles with `name`, `description`, and `developer_instructions`. The filename matches the role name. No role registration table is needed with the current format.

| Role | Model / reasoning | Responsibility |
| --- | --- | --- |
| `repository_scout` | Inherits subagent defaults: `gpt-6-luna` / `high` | Repository behavior, callers, contracts, and relevant documentation |
| `test_scout` | Inherits subagent defaults: `gpt-6-luna` / `high` | Tests, persistence, migrations, rollback/restore, and concurrency coverage |
| `architecture_reviewer` | Explicit `gpt-6.1-sol` / `medium` | Independent review of boundaries, transactions, locking, invariants, and issue scope |

All three roles set `sandbox_mode = "read-only"` and prohibit state-changing commands and external writes. They report evidence to the parent agent, which owns implementation and validation. Repository policy stays in `AGENTS.md` and its documentation routes; role files describe only their focused responsibilities. Delegate a bounded question when useful rather than loading every role or document for every task.

## Configuration ownership and precedence

Version the project configuration and role files. Keep personal preferences, authentication, providers, MCP setup, and machine-specific settings outside the repository in the user's Codex configuration.

For trusted projects, precedence is: CLI flags and `--config` overrides; project `.codex/config.toml` files from root to current directory (closest wins); selected user profile; user config; cloud-managed defaults; system config; built-in defaults. Enforced requirements can constrain these settings. Untrusted projects skip project-scoped `.codex/` layers; trust is a local user decision, not a versioned project setting.

For subagent model and reasoning, an explicit spawn value overrides `[agents]` defaults, which override the parent's values. Explicit values in a custom role file take precedence over that resolution. The reviewer therefore sets both model and reasoning; scouts use the shared defaults. Other omitted role settings inherit from the parent. Multi-agent tools are enabled by default in the current runtime, so no feature flag is required here.

## Compatibility and validation

Verified against Codex CLI `0.160.1` and current official OpenAI documentation on 2026-10-06:

- [Config Basics](https://developers.openai.com/codex/config-basic/) describes project layers, trust, and precedence.
- [Config Reference](https://developers.openai.com/codex/config-reference/) documents the root model/reasoning and `[agents]` default keys.
- [Subagents](https://developers.openai.com/codex/subagents/) documents standalone role files, read-only sandbox overrides, and per-role model/reasoning precedence.

Use `codex --version`, `codex --help`, and `codex debug models --bundled` to check the installed runtime and its model catalog. Both requested identifiers and their configured reasoning levels are advertised by this release. Catalog support does not guarantee availability for every account or provider.

The CLI has no dedicated `config validate` command. Use `codex --strict-config doctor --json` to inspect `config.load`; strict mode rejects unsupported configuration fields. Doctor also reports unrelated installation, provider/network, and personal MCP health, so inspect the configuration result separately from its overall status. The app-server `config/read` method can verify effective project values and layer origins without starting an inference session. Parse all project TOML files, compare role fields with the documented custom-agent schema, and check routed paths, repository quality checks, and `git diff --check` as well.
