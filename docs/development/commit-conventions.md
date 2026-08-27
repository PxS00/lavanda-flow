# Commit Conventions

Lavanda Flow follows Conventional Commits with an additional project rule: normal issue-driven commits and pull request titles must include the related GitHub issue number at the end.

## Required format

```text
<type>(<scope>): <description> (#<issue-number>)
```

Examples:

```text
feat(inventory): add stock withdrawal (#27)
fix(api): handle validation errors (#31)
test(inventory): cover FEFO selection edge cases (#27)
docs(git): document branch protection policy (#8)
chore(build): configure Maven Enforcer (#12)
ci(backend): add verification workflow (#12)
```

`ci(scope)` is valid. The scope identifies the affected area, for example `ci(backend)`, `ci(frontend)`, or `ci(github)`.

## Rules

- commit messages are written in English;
- the Conventional Commit type is required;
- the scope is required;
- the description is required;
- issue-driven commits must end with `(#<issue-number>)`;
- the issue reference must match the issue that owns the work;
- checkpoint commits within the same issue branch normally reference the same issue;
- do not append unrelated issue numbers merely because a commit touches nearby code;
- commits remain atomic by meaningful checkpoint.

The issue suffix is a Lavanda Flow convention layered on top of Conventional Commits. It is not part of the upstream Conventional Commits specification.

## Branch relationship

The primary purpose of an issue determines its normal-development branch prefix:

```text
feature -> feat
fix     -> fix
refactor -> refactor
test    -> test
docs    -> docs
chore   -> chore
ci      -> ci
```

The `feature` branch prefix intentionally maps to the Conventional Commit type `feat`. A branch can still contain secondary checkpoint commit types when they are required to complete the same issue; the branch prefix represents the issue's primary semantic purpose.

`hotfix/` is not a normal `fix/` branch. It is reserved for emergency production fixes that start from `main` and follow the dedicated hotfix policy.

## Pull request titles

Issue and hotfix PR titles follow the same format because issue PRs targeting `develop` are squash merged and the title becomes the integration commit message:

```text
feat(catalog): add inventory item registration (#18)
fix(inventory): prevent negative balance (#42)
docs(git): align branch prefixes with change types (#49)
chore(repository): pin development tool versions (#47)
```

Release PRs are the exception because one release normally aggregates multiple issues. A release PR title uses a scoped Conventional Commit title without a single issue suffix, for example:

```text
chore(release): prepare v0.1.0
```

The release PR body must list the included issues.

## Recommended types

```text
feat
fix
refactor
test
docs
chore
ci
build
perf
style
revert
```

The normal branch-prefix policy explicitly covers `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, and `ci`. Less common commit types such as `build`, `perf`, `style`, and `revert` do not require dedicated branch prefixes; use the approved issue-branch prefix that best represents the owning issue's primary purpose.

## Scope examples

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

Prefer stable project areas. Avoid scopes that merely repeat a class or file name.
