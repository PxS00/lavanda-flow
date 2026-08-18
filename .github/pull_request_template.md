## Summary

Describe what changed and why.

## Related issue

Closes #

## Change type

- [ ] `feat` — new functionality
- [ ] `fix` — bug fix
- [ ] `refactor` — refactoring without functional change
- [ ] `test` — tests
- [ ] `docs` — documentation
- [ ] `chore` — build, configuration, or maintenance
- [ ] `ci` — CI/CD

## Target branch

- [ ] Feature PR targeting `develop`
- [ ] Release PR targeting `main`
- [ ] Hotfix PR targeting `main`

## Scope

- [ ] Feature/hotfix PR title follows `<type>(<scope>): <description> (#<issue-number>)`.
- [ ] Release PR title follows `<type>(<scope>): <description>` and lists included issues in the body.
- [ ] The change matches the linked issue and approved scope.
- [ ] Module boundaries were preserved.
- [ ] No dependency was added without technical justification.
- [ ] No unrelated changes are hidden in this PR.

## Validation

Check only what applies:

- [ ] `./mvnw verify`
- [ ] relevant integration tests
- [ ] `ng lint`
- [ ] `ng test`
- [ ] `ng build`
- [ ] manual validation
- [ ] documentation updated

## Database

- [ ] No schema change.
- [ ] Schema changed and a compatible Flyway migration was added.

## API

- [ ] No HTTP contract change.
- [ ] OpenAPI/Swagger contract was updated or remains correctly inferred.

## Documentation

- [ ] Public Java contracts and non-obvious behavior have appropriate Javadoc where applicable.
- [ ] Architecture or operational documentation was updated when required.

## Release impact

For release PRs, summarize included issues, migrations, API changes, risks, and operational notes.

## Evidence / notes

Include relevant logs, screenshots, decisions, trade-offs, or known risks when useful.

## Merge policy

- Feature PRs into `develop`: **squash merge**.
- Release PRs from `release/vX.Y.Z` into `main`: **regular merge commit**.
- Hotfixes follow the documented policy in `docs/development/git-workflow.md`.
