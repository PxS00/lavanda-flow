# Contributing to Lavanda Flow

Lavanda Flow uses issue-driven development, Conventional Commits, atomic checkpoint commits, pull requests, and a controlled release flow.

Before contributing, read:

- `AGENTS.md`
- `docs/product/scope-v1.md`
- `docs/development/git-workflow.md`
- `docs/architecture/architecture.md`
- `docs/architecture/backend-structure.md`
- `docs/architecture/dependencies.md`

## Development flow

1. Start from an approved GitHub issue.
2. Update local `develop`.
3. Create a branch using:

```text
features/<issue-number>/<short-description>
```

4. Implement the issue using coherent checkpoint commits.
5. Run all relevant validation.
6. Open a pull request targeting `develop`.
7. Merge the feature PR using squash merge.
8. Releases are prepared from `develop` using `release/vX.Y.Z`.
9. Release branches reach `main` only through a release pull request.

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

Use Conventional Commits.

Examples:

```text
feat(inventory): register stock entry
fix(inventory): reject withdrawal above available balance
test(inventory): cover expired batch selection
docs: document release workflow
chore(build): configure Maven Enforcer
ci: verify backend build
```

Commits should be atomic by meaningful checkpoint. Do not create a commit for every trivial edit, and do not accumulate a large feature into one oversized commit containing unrelated work.

## Pull requests

Feature PRs target `develop` and are squash merged.

A PR should:

- link the issue;
- remain within the issue scope;
- include tests for relevant behavior;
- update documentation when necessary;
- contain no secrets;
- avoid unrelated changes;
- pass the applicable CI checks.

Release PRs target `main` from `release/vX.Y.Z` and follow the release requirements documented in `docs/development/git-workflow.md`.

## Architecture

Do not bypass documented module boundaries or introduce dependencies outside the approved stack without technical justification.

Structural decisions that affect multiple areas should be documented through an ADR before becoming project-wide conventions.
