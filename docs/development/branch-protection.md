# Branch Protection Configuration

## Purpose

This document records the GitHub branch-protection configuration that enforces the workflow defined in `docs/development/git-workflow.md`.

The Git workflow document remains the source of truth for branch purpose, release flow, hotfix flow, and merge strategy. This document records the repository settings and required CI contexts that implement that policy.

## Protected branches

The protected branches are:

- `develop` — integration branch for the next release;
- `main` — production-ready branch.

Both branches require pull requests and successful required checks before merge. Force pushes and branch deletion are blocked.

## Required status checks

GitHub branch rules must reference the status-check contexts published by workflow jobs rather than workflow display names.

The required contexts are:

```text
validate-title
repository-quality
Maven verify
Angular verify
```

They map to the repository workflows as follows:

| Required context | Workflow | Responsibility |
| --- | --- | --- |
| `validate-title` | Pull Request Title | Enforces the project PR-title convention. |
| `repository-quality` | Repository Quality | Verifies repository documentation and whitespace rules. |
| `Maven verify` | Backend Verification | Runs backend verification when backend-related files change and otherwise completes successfully as a no-op gate. |
| `Angular verify` | Frontend Verification | Runs frontend verification when frontend-related files change and otherwise completes successfully as a no-op gate. |

Required checks must always reach a terminal state for pull requests targeting a protected branch. For this reason, pull-request path filters are not used on required workflows.

Backend and frontend workflows remain path-aware internally. They detect whether their respective scope changed and skip expensive setup/build steps when verification is not relevant to the pull request while keeping the required job successful.

Push triggers may continue using path filters because required merge gates are evaluated on pull requests.

## `develop` ruleset

`develop` uses the following policy:

- require a pull request before merging;
- require `validate-title`;
- require `repository-quality`;
- require `Maven verify`;
- require `Angular verify`;
- require all review conversations to be resolved before merging;
- require zero approving reviews while the repository has a single active maintainer;
- do not require the pull request branch to be updated with the latest `develop` before merge;
- allow squash merge as the branch merge method;
- block force pushes;
- block branch deletion;
- do not configure normal-development bypass actors.

Normal integration is:

```text
feature/<issue-number>/<short-description> -> develop
```

Feature pull requests are squash merged so the PR title becomes the integration commit message.

## `main` ruleset

`main` uses the following policy:

- require a pull request before merging;
- require `validate-title`;
- require `repository-quality`;
- require `Maven verify`;
- require `Angular verify`;
- require all review conversations to be resolved before merging;
- require zero approving reviews while the repository has a single active maintainer;
- require the pull request branch to be up to date with the latest `main` before merge;
- allow regular merge commits as the branch merge method;
- block force pushes;
- block branch deletion;
- do not configure normal-development bypass actors.

Normal production integration is:

```text
release/vX.Y.Z -> main
```

Emergency production fixes use the documented `hotfix/<issue-number>/<short-description> -> main` pull-request flow and must be synchronized back into `develop`.

## Repository merge methods

Repository-level merge settings allow:

- squash merge;
- regular merge commits.

Rebase merge remains disabled.

The branch rulesets narrow the allowed method for each protected branch:

```text
develop -> squash merge
main    -> regular merge commit
```

`Require linear history` must remain disabled on `main` because release pull requests intentionally use regular merge commits.

## Review policy

The repository currently has a single active maintainer. Requiring an approving review would not provide independent review and could make the documented self-managed workflow impossible to complete.

Protection therefore relies on:

- mandatory pull requests;
- required CI checks;
- resolved review conversations;
- protected merge methods;
- blocked force pushes and deletion.

When another active maintainer becomes available, revisit this policy and require at least one independent approval.

## Deliberately disabled gates

The following controls are not required merge gates at the Foundation stage:

- signed commits;
- Code Owner approval;
- code scanning results;
- code quality results;
- code coverage thresholds;
- deployment/environment success;
- merge queue;
- automatic Copilot review.

They may be introduced later only after the corresponding capability is intentionally configured and documented.

## Operational rule

Do not add a workflow as a required status check while that workflow can be skipped entirely for a protected-branch pull request. A required context must always be reported so pull requests cannot become permanently blocked waiting for a check that never runs.
