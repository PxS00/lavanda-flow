# Issue Closing Automation

## Purpose

Lavanda Flow keeps `main` as the repository default and production branch while normal issue-driven pull requests target `develop`.

GitHub closing keywords such as `Closes #70` close issues natively only when the pull request is merged into the repository default branch. Because normal development is integrated into `develop` first, the repository provides an explicit workflow that applies the same completion semantics after a successful merge into `develop`.

## Behavior

`.github/workflows/close-linked-issues.yml` runs when a pull request targeting `develop` is closed.

The job proceeds only when the pull request was actually merged. It reads the pull request body and recognizes same-repository issue references using the standard closing keyword families:

- `Close`, `Closes`, `Closed`;
- `Fix`, `Fixes`, `Fixed`;
- `Resolve`, `Resolves`, `Resolved`.

Keywords are case-insensitive and must be followed by an issue reference such as `#70`.

Multiple references are deduplicated. Pull request numbers are ignored if referenced accidentally, already-closed issues are skipped, and open issues are closed with the GitHub state reason `completed`.

## Pull request convention

Normal issue pull requests must keep the existing repository convention:

```text
Closes #<issue-number>
```

The reference belongs in the pull request body under `Related issue`.

## Branch semantics

This automation does not change the branch model:

- `main` remains the default and production branch;
- normal issue branches target `develop`;
- issue pull requests are squash-merged into `develop`;
- release branches promote completed work from `develop` to `main`.

The workflow exists only to align issue lifecycle with the repository's integration model without redefining `develop` as the default branch.

## Permissions and safety

The workflow uses the repository-provided `GITHUB_TOKEN` with only the permissions required to read pull request context and update issues.

It does not check out or execute code from the merged branch. The pull request body is treated only as text used to identify same-repository issue numbers.
