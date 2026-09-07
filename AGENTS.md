# Lavanda Flow agent instructions

## Project and source of truth

Lavanda Flow is Céu de Lavanda's inventory and production management system. V1 covers operational inventory and the approved minimum internal-production and recursive batch-traceability workflow. Unrelated ERP and manufacturing expansion, including costs and margins, sales and fiscal features, purchasing automation, and broader manufacturing automation, remains outside V1.

The current GitHub issue is the implementation specification. Before changing anything, read its Objective, Context, Scope, Acceptance Criteria, Constraints, and Out of Scope; then read the documentation relevant to the affected area.

Authoritative documentation:

- Product scope: `docs/product/scope-v1.md`
- Domain model: `docs/domain/domain-model.md`
- Architecture and data model: `docs/architecture/architecture.md`, `docs/architecture/data-model.md`
- Approved dependencies: `docs/architecture/dependencies.md`
- Backend structure: `docs/architecture/backend-structure.md`
- Git workflow and commits: `docs/development/git-workflow.md`, `docs/development/commit-conventions.md`
- Contribution expectations: `CONTRIBUTING.md`

More-specific instructions apply in `backend/AGENTS.md` and `frontend/AGENTS.md`; they refine this file and must not contradict it.

## Architecture and scope

- Build a modular monolith, organized by feature/domain rather than global technical layers.
- Respect Spring Modulith module boundaries and communicate with other modules only through their public APIs; never import another module's internal infrastructure.
- Keep business rules out of controllers and frontend components.
- Do not add speculative modules, abstractions, events, dependencies, or architecture.
- Preserve existing contracts and implement the smallest complete solution within the issue scope.
- Identify the responsible module before editing. If a missing structural decision materially affects the solution, raise or record an architectural decision rather than silently choosing one.

## Inventory invariants

- Use `BigDecimal` for quantities; never use `double` or `float`.
- Stock cannot become negative.
- Every stock-changing operation creates auditable history. Corrections create new adjustment movements rather than rewriting history.
- Stock-balance operations are transactional.
- PostgreSQL is the source of truth and Flyway controls schema evolution.
- FEFO, available stock, expiration, and inventory eligibility are backend-authoritative.
- `expiresAt <= today` means expired. Use the application `Clock` for date-dependent business rules and tests; do not use absolute future test dates that will eventually expire.

## Language

- Operator-facing application content is pt-BR.
- Engineering artifacts are English.
- Do not translate routes, DTOs, enums, API contracts, or wire values.

## Validation

Run the relevant validation for the changed area:

- Backend: `./mvnw verify`
- Frontend: `pnpm lint`, `pnpm test`, `pnpm build`

## Git and pull requests

Follow the detailed Git documentation. In brief:

- `main` is production; `develop` is next-release integration.
- Normal issue branches start from `develop`; issue PRs target `develop` and use squash merge.
- Releases use `release/vX.Y.Z` and a regular merge into `main`.
- Use Conventional Commits in English. Normal issue-driven commits use `<type>(<scope>): <description> (#<issue>)`.
