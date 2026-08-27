# Contributing to Lavanda Flow

Lavanda Flow uses issue-driven development, scoped Conventional Commits, atomic checkpoint commits, pull requests, and a controlled release flow.

Before contributing, read:

- `AGENTS.md`
- `docs/product/scope-v1.md`
- `docs/development/git-workflow.md`
- `docs/development/commit-conventions.md`
- `docs/architecture/architecture.md`
- `docs/architecture/backend-structure.md`
- `docs/architecture/dependencies.md`

## Development flow

1. Start from an approved GitHub issue.
2. Update local `develop`.
3. Create an issue branch using the prefix that matches the primary change type:

```text
feature/<issue-number>/<short-description>   # feat
fix/<issue-number>/<short-description>       # fix
refactor/<issue-number>/<short-description>  # refactor
test/<issue-number>/<short-description>      # test
docs/<issue-number>/<short-description>      # docs
chore/<issue-number>/<short-description>     # chore
ci/<issue-number>/<short-description>        # ci
```

4. Implement the issue using coherent checkpoint commits.
5. Run all relevant validation.
6. Open a pull request targeting `develop`.
7. Merge the issue PR using squash merge.
8. Releases are prepared from `develop` using `release/vX.Y.Z`.
9. Release branches reach `main` only through a release pull request.

Emergency production fixes are the exception: they use `hotfix/<issue-number>/<short-description>` from `main` and follow the dedicated hotfix flow.

See `docs/development/git-workflow.md` for the complete policy.

## Language

Engineering artifacts are written in English:

- branches;
- commits;
- issues;
- pull requests;
- source code identifiers;
- code comments when necessary;
- technical documentation;
- ADRs;
- CI/CD and operational documentation.

User-facing product text may be Portuguese.

## Commits

Use Conventional Commits with a mandatory scope and, for issue-driven work, the related issue number at the end.

Required format:

```text
<type>(<scope>): <description> (#<issue-number>)
```

Examples:

```text
feat(inventory): register stock entry (#18)
fix(inventory): reject withdrawal above available balance (#21)
test(inventory): cover expired batch selection (#21)
docs(git): document release workflow (#8)
chore(build): configure Maven Enforcer (#12)
ci(backend): verify backend build (#12)
```

`ci(scope)` is valid. For example, `ci(backend)`, `ci(frontend)`, and `ci(github)` identify the CI area being changed.

Choose a stable scope that identifies the affected module or engineering concern. Avoid scopes that are so granular that they merely repeat a class or file name.

Commits should be atomic by meaningful checkpoint. Do not create a commit for every trivial edit, and do not accumulate a large issue into one oversized commit containing unrelated work.

The issue suffix is a Lavanda Flow convention on top of Conventional Commits. Release PRs are exempt from a single issue suffix because they aggregate multiple issues.

See `docs/development/commit-conventions.md` for the complete commit policy.

## Code documentation

Code should be documented at the appropriate boundary rather than filled with redundant comments.

For Java:

- use Javadoc for public APIs, reusable contracts, domain concepts, application services, extension points, and non-obvious behavior;
- document important invariants, side effects, exceptional conditions, and semantics that are not obvious from names and types;
- use `@param`, `@return`, `@throws`, and `@since` when they add meaningful contract information;
- avoid Javadoc that merely restates a method or field name;
- avoid comments that explain syntax instead of intent;
- keep documentation synchronized with behavior when code changes.

HTTP contracts are documented through OpenAPI/Swagger. Architectural decisions belong in ADRs. Repository and operational behavior belongs in project documentation.

## Pull requests

Issue PRs target `develop` and are squash merged.

Because the PR title becomes the squash commit message, issue and hotfix PR titles must use:

```text
<type>(<scope>): <description> (#<issue-number>)
```

Examples:

```text
feat(catalog): add inventory item registration (#18)
chore(repository): pin development tool versions (#47)
docs(git): align branch prefixes with change types (#49)
```

Release PRs use scoped Conventional Commit titles without a single issue suffix, for example:

```text
chore(release): prepare v0.1.0
```

A PR should:

- link the issue;
- remain within the issue scope;
- use a branch prefix that reflects the issue's primary semantic type;
- include tests for relevant behavior;
- update code and project documentation when necessary;
- contain no secrets;
- avoid unrelated changes;
- pass the applicable CI checks.

Release PRs target `main` from `release/vX.Y.Z` and follow the release requirements documented in `docs/development/git-workflow.md`.

## Branch protection

Once issue-driven development begins, `main` and `develop` must be protected according to `docs/development/git-workflow.md`.

At minimum:

- pull requests are required;
- applicable CI checks are required;
- force pushes are disabled;
- `main` cannot be deleted;
- normal issue-driven work does not target `main` directly.

## Code ownership

Repository ownership rules are defined in `.github/CODEOWNERS`.

## Security

Do not report secrets or exploitable vulnerabilities in public issues. Follow `SECURITY.md` for responsible reporting.

## Architecture

Do not bypass documented module boundaries or introduce dependencies outside the approved stack without technical justification.

Structural decisions that affect multiple areas should be documented through an ADR before becoming project-wide conventions.
