# Git Workflow and Contribution Policy

## Purpose

This document defines the Git workflow, branch strategy, commit policy, pull request flow, release process, and language conventions for Lavanda Flow.

Once functional development starts, `main` is treated as the production branch and must not receive normal feature work directly.

Commit formatting details are defined in `docs/development/commit-conventions.md`.

## Branch model

```text
feature/<issue-number>/<short-description>
                │
                │ Pull Request + squash merge
                ▼
             develop
                │
                │ create release branch
                ▼
        release/v<major>.<minor>.<patch>
                │
                │ Release Pull Request
                ▼
              main
```

### `main`

`main` represents production-ready code.

Rules:

- no normal feature development directly on `main`;
- no direct commits after the initial repository/bootstrap phase is finished;
- changes normally reach `main` only through a release pull request;
- every merge to `main` must represent a release-ready state;
- CI checks required for the current project stage must pass before merge;
- force-push and branch deletion must be disabled through branch protection;
- pull requests are required for normal changes;
- release tags use Semantic Versioning, for example `v0.1.0`, `v0.2.0`, and `v1.0.0`.

### `develop`

`develop` is the integration branch for the next release.

Rules:

- feature branches start from an up-to-date `develop`;
- feature pull requests target `develop`;
- completed feature pull requests are merged using **squash merge**;
- `develop` must remain buildable and testable;
- unfinished or knowingly broken work must not be merged into `develop`;
- force-push must be disabled through branch protection;
- pull requests and required CI checks are mandatory once project CI exists.

### Feature branches

Functional work must use:

```text
feature/<issue-number>/<short-description>
```

Examples:

```text
feature/12/bootstrap-spring-backend
feature/18/register-inventory-item
feature/27/implement-fefo-selection
```

Rules:

- branch names are written in English;
- use lowercase kebab-case for the description;
- every feature branch must reference an existing issue;
- branch from `develop`;
- keep the branch focused on the issue scope;
- checkpoint commits normally reference the same owning issue at the end of the commit message;
- open a pull request to `develop` when the work is ready for integration;
- merge into `develop` with squash merge.

Technical work may use the same branch pattern when it belongs to an issue. If a dedicated prefix becomes necessary later, it must be documented before being adopted globally.

### Release branches

When `develop` contains the complete scope planned for a release, create:

```text
release/v<major>.<minor>.<patch>
```

Examples:

```text
release/v0.1.0
release/v0.2.0
release/v1.0.0
```

A release branch is created from `develop`.

Allowed work on a release branch is limited to release stabilization, such as:

- version metadata;
- release documentation;
- final configuration adjustments;
- small release-blocking fixes;
- fixes discovered during final validation.

Do not introduce unrelated features into a release branch.

When the release is validated, open a **Release Pull Request** from the release branch to `main`.

The release PR should use a regular merge commit rather than squash merging the complete release. Feature work has already been squashed individually when entering `develop`; preserving those commits on `main` keeps the release history readable at feature granularity.

After the release reaches `main`:

1. create the corresponding Git tag, such as `v0.1.0`;
2. synchronize release changes back into `develop` when the release branch received stabilization changes not already present there;
3. delete the release branch after synchronization.

## Hotfixes

Emergency production fixes are exceptional.

When required, use:

```text
hotfix/<issue-number>/<short-description>
```

A hotfix starts from `main`, receives focused validation, and reaches `main` through a pull request. Hotfix commits and the hotfix PR title must reference the owning issue. The resulting fix must then be synchronized back into `develop` so the branches do not diverge.

Hotfixes must not be used to bypass the normal release process for ordinary development.

## Pull requests

### Feature PR

```text
feature/<issue>/<description> -> develop
```

Requirements:

- issue linked in the PR;
- scope matches the issue;
- tests and validation appropriate to the change are complete;
- documentation is updated when behavior, architecture, configuration, or contracts change;
- no unrelated refactoring or dependency changes are hidden in the PR;
- squash merge is used;
- PR title must follow the issue-linked scoped Conventional Commit format because it becomes the squash commit message.

Required title format:

```text
<type>(<scope>): <description> (#<issue-number>)
```

Examples:

```text
feat(inventory): add inventory item registration (#18)
fix(api): return validation errors consistently (#31)
ci(backend): add backend verification workflow (#12)
```

### Release PR

```text
release/vX.Y.Z -> main
```

A release PR may omit a single issue suffix because it aggregates multiple issues.

Recommended title format:

```text
chore(release): prepare v0.1.0
```

The release PR must summarize:

- release scope;
- included issues/features;
- migrations;
- API contract changes;
- known risks;
- validation performed;
- deployment or operational notes when applicable.

A release PR is not a place for new feature development.

## Commit policy

Lavanda Flow follows **Conventional Commits** with commit messages written in English.

For issue-driven work, the scope and issue reference are mandatory.

Required format:

```text
<type>(<scope>): <description> (#<issue-number>)
```

Examples:

```text
feat(inventory): add stock withdrawal use case (#27)
fix(inventory): prevent negative batch balance (#27)
test(inventory): cover FEFO selection edge cases (#27)
refactor(catalog): isolate item classification policy (#18)
docs(git): define release branch workflow (#8)
chore(build): configure Maven Enforcer (#12)
ci(backend): add backend verification workflow (#12)
```

`ci(scope)` is valid. Examples include `ci(backend)`, `ci(frontend)`, and `ci(github)`.

Prefer stable, meaningful scopes such as:

```text
inventory
catalog
suppliers
backend
frontend
api
security
database
observability
build
ci
github
git
docs
release
```

Do not use file names or individual class names as scopes unless there is a strong reason.

The `(#<issue-number>)` suffix is a Lavanda Flow convention layered on top of Conventional Commits. It is required for normal issue-driven commits and feature/hotfix PR titles. Release PRs are the primary exception because they aggregate multiple issues.

### Atomic commits by checkpoint

Commits must be atomic at a meaningful development checkpoint.

Atomic does **not** mean creating a commit after every file or every small edit. It means each commit should represent one coherent change that can be understood and reviewed independently.

Good checkpoints include:

- introducing one domain concept with its tests;
- completing one use case;
- adding one migration together with the persistence change that requires it;
- configuring one build or CI capability;
- completing one focused refactor;
- updating documentation for one architectural decision.

Avoid both extremes:

```text
Bad: dozens of tiny commits for trivial edits
Bad: one commit containing thousands of unrelated changes
```

A feature branch with meaningful complexity should normally contain multiple checkpoint commits during development. The exact number is not a target; coherence is.

Before committing, ask:

> Does this commit represent one coherent checkpoint, and would its diff make sense on its own?

If the answer is no, regroup or split the changes.

### Commit scope

Do not mix unrelated concerns in the same commit.

For example, avoid combining all of these unless they are inseparable for one checkpoint:

- backend feature implementation;
- unrelated frontend formatting;
- dependency upgrades;
- documentation cleanup;
- CI refactoring.

## Branch protection policy

Once issue-driven development starts, configure GitHub branch protection so repository settings enforce the documented flow.

### `main`

Expected protection:

- require a pull request before merging;
- require status checks applicable to the release;
- block force pushes;
- block branch deletion;
- do not use normal feature branches as PR sources;
- normal integration path is `release/* -> main`;
- emergency path is `hotfix/* -> main`.

### `develop`

Expected protection:

- require a pull request before merging;
- require backend/frontend CI checks once available;
- block force pushes;
- keep the branch buildable;
- normal integration path is `feature/* -> develop`.

Branch protection is a repository setting, while this document is the source of truth for the intended policy.

## Language policy

The repository uses **English as the engineering language**.

The following must be written in English:

- branch names;
- commit messages;
- issue titles and bodies;
- pull request titles and descriptions;
- source code identifiers;
- code comments when comments are necessary;
- architecture documentation;
- development documentation;
- ADRs;
- API documentation;
- operational and CI documentation.

User-facing application content may remain in Portuguese because the primary product users are Portuguese-speaking.

Existing repository documentation written before this policy should be migrated to English as it is revised, and preferably before functional development becomes substantial.

## Issue-driven development

Normal development starts from an issue.

Expected flow:

```text
Issue
  ↓
feature/<issue>/<description>
  ↓
checkpoint commits (#issue)
  ↓
Feature PR (#issue)
  ↓ squash
 develop
  ↓
release/vX.Y.Z
  ↓
Release PR
  ↓
 main
```

The issue defines the problem and acceptance criteria. The branch implements it. The PR validates and integrates it.

## Pre-merge expectations

Before a feature PR is merged into `develop`:

- relevant tests pass;
- lint/format validation passes;
- build passes;
- database migrations are included when required;
- OpenAPI contracts are updated when required;
- architectural boundaries are preserved;
- no secrets are committed;
- the final diff has been reviewed for unrelated changes.

Before a release PR is merged into `main`, all CI checks required for both backend and frontend must pass once those workflows exist.

## Initial repository exception

During the initial documentation, governance, and bootstrap setup, direct commits to `main` are intentionally allowed because the production branch model is not yet active.

This exception ends when issue-driven functional development begins and the `develop` branch is established.
