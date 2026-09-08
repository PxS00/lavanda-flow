# Issue #183 — Validate local operational runtime

## Objective

Add the smallest CI protection needed to prove that the v0.6.0 local operational runtime introduced by #182 can still be produced from reviewed source without a registry, cloud deployment, workstation cache, or operational secrets.

The CI change must protect the repository-owned Docker/Compose runtime while preserving the existing required merge gates and their status-check contexts.

## Source of truth

Apply, in order:

1. GitHub issue #183;
2. `AGENTS.md`;
3. ADR 0010: `docs/architecture/decisions/0010-adopt-local-first-operator-hosted-runtime.md`;
4. `docs/specs/0182-add-local-operational-runtime.md`;
5. `docs/operations/local-operational-runtime.md`;
6. `docs/development/branch-protection.md`;
7. existing workflows under `.github/workflows/`;
8. `Dockerfile`, `.dockerignore`, `compose.operational.yaml`, and `operational.env.example`;
9. this specification for the approved implementation details of #183.

Issue #183 remains authoritative for objective, scope, acceptance criteria, constraints, and out-of-scope behavior.

## Branch

```text
ci/183/validate-local-operational-runtime
```

The branch starts from `develop` after #182 was squash-merged.

## Dependency state

#182 is complete. The operational runtime now exists on `develop` as:

```text
Dockerfile
.dockerignore
compose.operational.yaml
operational.env.example
backend/src/main/resources/application-operational.yml
```

with the fixed runtime topology:

```text
lavanda-flow-operational
├── lavanda-flow-app
└── postgres
```

#183 must validate that exact runtime path. It must not create a second packaging path, second Compose definition, registry flow, or deployment workflow.

## Existing CI contract to preserve

The repository already has required protected-branch contexts:

```text
validate-title
repository-quality
Maven verify
Angular verify
```

`docs/development/branch-protection.md` requires those contexts to remain terminal on every protected-branch pull request. Backend and frontend workflows therefore use internal change detection instead of pull-request path filters.

#183 must not rename, remove, or weaken any of those required contexts.

The existing `repository-quality` context is the correct enforcement anchor for #183 because it is already a required repository-level gate. Do not require a new branch-protection administration change solely to make runtime CI enforceable.

## Approved workflow design

Extend `.github/workflows/repository-quality.yml` while preserving the required status-check context `repository-quality`.

Refactor that workflow into three jobs:

```text
repository-checks
operational-runtime
repository-quality
```

### `repository-checks`

Move the current repository-quality implementation into this internal job without weakening it.

It must continue to:

- check trailing whitespace in tracked repository documentation/YAML scope as currently implemented;
- verify the required repository documentation set;
- use read-only repository permissions.

Do not broaden unrelated repository policy in #183.

### `operational-runtime`

Add one internal job dedicated to #182 runtime validation.

It must always reach a terminal successful state on protected-branch pull requests, including when runtime validation is not relevant. Use internal change detection rather than pull-request path filters.

It should run on `ubuntu-latest` with a bounded timeout appropriate to an image build.

Use only GitHub-hosted runner capabilities already available to the workflow. Do not add an external CI platform, registry, build SaaS, or paid service.

### `repository-quality`

Keep this exact job id as the required aggregate gate.

It must:

- depend on both `repository-checks` and `operational-runtime`;
- run with `if: always()` so the required context always reaches a terminal state;
- fail if either dependency fails or is cancelled unexpectedly;
- succeed only when both internal jobs complete successfully or the runtime job explicitly completes its no-op path successfully.

This preserves the existing branch-protection context without requiring an administrative ruleset change.

Do not rename the required context.

## Runtime relevance detection

For pull requests, the operational-runtime job must determine whether the reviewed diff changes a runtime input.

Treat at least the following as runtime-relevant:

```text
Dockerfile
.dockerignore
compose.operational.yaml
operational.env.example
backend/**
frontend/**
.tool-versions
.github/workflows/repository-quality.yml
```

Rationale:

- backend changes can break the packaged JAR or runtime startup contract;
- frontend changes can break the Angular production build or packaged SPA output;
- Docker/Compose/environment-template changes directly change runtime packaging;
- tool-version changes can invalidate the build contract;
- changes to the validating workflow must exercise their own validation path.

Do not treat documentation-only changes as runtime-relevant unless they also modify one of the paths above.

Use the pull request base/head SHAs from the GitHub event and a full-enough checkout to perform reliable diff detection.

For non-pull-request events supported by the workflow, use the smallest deterministic behavior that does not weaken the pull-request gate. Do not add deployment behavior.

## Runtime validation steps

When runtime-relevant changes are detected, validate the exact #182 runtime path.

### 1. Required runtime files

Fail clearly if any required repository runtime file is missing:

```text
Dockerfile
.dockerignore
compose.operational.yaml
operational.env.example
```

Do not duplicate the runtime implementation into CI-generated files.

### 2. Required external configuration behavior

Verify that the operational Compose configuration still rejects missing mandatory database configuration.

The validation must not depend on actual workstation secrets.

Unset or isolate these variables when checking the failure path:

```text
POSTGRES_DB
POSTGRES_USER
POSTGRES_PASSWORD
```

The command is expected to fail because #182 deliberately uses required Compose interpolation.

Do not log real credentials or loosen the required-variable contract to make CI easier.

### 3. Temporary CI-only environment

Create a temporary environment file under the runner temporary directory for successful Compose validation.

Use:

- a clearly non-production database name;
- a clearly non-production database username;
- a generated ephemeral password that is written directly to the temporary file and never printed;
- no operator bootstrap password;
- bootstrap disabled.

Delete the temporary file through shell cleanup/trap behavior where practical.

Do not use GitHub repository secrets because no real secret is required for this validation.

Do not copy the temporary environment file into the Docker build context or image layers.

### 4. Compose validation

Validate the existing operational Compose file with the temporary CI environment using the repository's real command shape:

```text
docker compose -f compose.operational.yaml --env-file <temporary-env> config --quiet
```

Do not print the fully rendered Compose configuration because it contains the temporary database password.

Also verify without exposing the rendered secret values that:

- the service set is exactly `postgres` and `lavanda-flow-app`;
- `postgres` has no published host port;
- the application remains the only service with a published HTTP port;
- the operational project/volume definition remains parseable.

Use standard tools available on `ubuntu-latest`; avoid adding a dependency only for these assertions.

### 5. Clean operational image build

Build the application through the operational Compose path, not through an alternate standalone CI Docker command that bypasses #182 configuration.

Use the equivalent of:

```text
docker compose -f compose.operational.yaml --env-file <temporary-env> build --no-cache lavanda-flow-app
```

The build must start from the clean GitHub checkout and must not rely on a developer workstation cache for correctness.

A GitHub-hosted runner or dependency download cache may later be used only as an optimization if correctness remains independent of it. Do not introduce such optimization unless needed by the issue.

Do not start or deploy the operator runtime in #183. #182 already performed the local runtime smoke validation; #183 only needs reproducible CI packaging/config validation.

### 6. Revision evidence

After a successful build, record non-sensitive evidence identifying the validated Git revision.

At minimum:

- resolve the checked-out commit with Git;
- include that revision in the GitHub step summary or equivalent non-secret CI output;
- confirm the locally built application image exists.

A local CI-only image tag using the Git revision is acceptable.

Do not publish that image.

Do not add GHCR, Docker Hub, artifact registry, image signing infrastructure, release upload, or deployment.

## Secret and logging constraints

The workflow must use:

```yaml
permissions:
  contents: read
```

unless the existing workflow requires an equally narrow equivalent.

Do not request:

- `packages: write`;
- `id-token: write`;
- deployment/environment permissions;
- cloud credentials;
- registry credentials.

No operational database password, operator password, session secret, private IP, or workstation-specific value may appear in workflow source or logs.

The CI-only generated PostgreSQL password is ephemeral and must not be echoed.

Avoid commands that print the fully interpolated Compose model.

## Existing backend/frontend gates

Do not modify or weaken:

```text
.github/workflows/backend-verification.yml
.github/workflows/frontend-verification.yml
.github/workflows/pr-title.yml
```

unless a concrete blocker is discovered.

Their current behavior remains:

- backend changes run `Maven verify`;
- frontend changes run Angular verification;
- unrelated PRs complete those required jobs successfully through their current no-op path;
- PR title validation remains unchanged.

Runtime validation is additive through the existing required `repository-quality` context.

## Branch-protection documentation

Update:

```text
docs/development/branch-protection.md
```

only as needed to keep the documented `repository-quality` responsibility accurate.

The required context list remains exactly:

```text
validate-title
repository-quality
Maven verify
Angular verify
```

Document that `repository-quality` now aggregates:

- repository/documentation quality checks;
- path-aware operational runtime validation.

No GitHub ruleset mutation is required because the required status-check context does not change.

## Out of scope implementation

Do not add:

- GHCR or other registry login/push;
- Docker image publication;
- GitHub Release assets;
- cloud/VPS deployment;
- SSH/workstation deployment;
- self-hosted runners;
- #184 backup execution;
- #185 go-live procedures;
- #186 workstation startup/network configuration;
- product/backend/frontend behavior changes;
- a second Dockerfile or second operational Compose path;
- semantic image tags intended for publication;
- registry credentials or cloud secrets.

## Validation strategy

Because #183 changes CI/repository configuration rather than application behavior, validate the changed workflow and the exact runtime path rather than rerunning unrelated product tests solely for ceremony.

Before finalizing locally:

1. inspect the complete workflow diff;
2. run `git diff --check`;
3. if Docker is available, execute the same required-config failure check, safe `config --quiet` validation, service/network assertions, and no-cache application build used by the workflow;
4. confirm no generated `.env` or runtime secret is tracked;
5. confirm backend/frontend/title workflows are unchanged unless a reported blocker required otherwise;
6. run `git status --short`.

The pull request itself is the authoritative GitHub Actions syntax/execution validation. After push, all existing required checks plus the updated `repository-quality` aggregate must complete successfully before merge.

If Docker is unavailable locally, report the omitted local runtime checks; do not fake them.

## Test/review cases

Review the workflow against at least these cases:

### Documentation-only PR

- `repository-checks` runs normally;
- `operational-runtime` detects no relevant runtime changes and succeeds through the no-op path;
- aggregate `repository-quality` succeeds;
- no expensive Docker build runs.

### Backend change

- existing `Maven verify` gate runs;
- operational runtime validation also runs because backend output is packaged into the image;
- image builds successfully through `compose.operational.yaml`;
- no registry publication occurs.

### Frontend change

- existing Angular gate runs;
- operational runtime validation also runs because Angular production output is packaged into the image;
- image builds successfully through `compose.operational.yaml`.

### Runtime configuration change

A change to Dockerfile/Compose/environment template/tool versions or the validation workflow itself triggers operational runtime validation even when backend/frontend source is untouched.

### Runtime failure

A missing runtime file, invalid Compose configuration, exposed PostgreSQL port, unexpected service topology, or failed image build causes `operational-runtime` to fail and therefore causes the required aggregate `repository-quality` context to fail.

### Missing required database configuration

Compose must still reject absent mandatory database variables. CI must treat unexpected success as a regression.

## Acceptance criteria mapping

### CI builds the #182 runtime

Satisfied when runtime-relevant PRs build `lavanda-flow-app` through `compose.operational.yaml` from the clean reviewed checkout.

### No workstation cache dependency

Satisfied by the GitHub-hosted clean checkout and no-cache operational image build.

### No registry/publication requirement

Satisfied by local runner image build/inspection only, with no login, push, registry permission, or deployment step.

### No secret leakage

Satisfied by generated ephemeral CI-only database configuration, `config --quiet`, no rendered Compose output, and read-only workflow permissions.

### Existing merge gates remain intact

Satisfied by retaining `validate-title`, `Maven verify`, `Angular verify`, and the exact required aggregate context `repository-quality`.

### Container/runtime definitions validated

Satisfied by required-file checks, Compose required-variable behavior, syntax validation, exact service topology/private PostgreSQL assertions, and the actual operational image build.

### Least privilege

Satisfied by `contents: read` only.

### No automatic deployment

Satisfied by stopping after local runner build/inspection evidence.

## Stop conditions

Stop and report before expanding scope if implementation appears to require any of the following:

- changing the #182 two-service runtime topology;
- publishing images to make CI work;
- adding registry/cloud credentials;
- changing application code to make CI pass;
- changing required status-check names;
- adding a self-hosted runner;
- deploying to the operator notebook;
- weakening required database configuration or runtime security.

A concrete GitHub Actions/runtime incompatibility may justify a narrowly scoped adjustment, but it must be reported rather than silently redesigning the release/runtime model.
