# Lavanda Flow agent instructions

## Project and source of truth

Lavanda Flow is Céu de Lavanda's inventory and production management system. V1 covers operational inventory and the approved minimum internal-production and recursive batch-traceability workflow. Unrelated ERP and manufacturing expansion, including costs and margins, sales and fiscal features, purchasing automation, and broader manufacturing automation, remains outside V1.

The current GitHub issue and its versioned spec in `docs/specs/`, when present, are the task source of truth. Before changing anything, read the issue's Objective, Context, Scope, Acceptance Criteria, Constraints, and Out of Scope and its spec. If they conflict, raise the discrepancy before implementing the affected behavior.

More-specific instructions apply in `backend/AGENTS.md` and `frontend/AGENTS.md`; they refine this file and must not contradict it.

## Documentation routing

Read only the documents and sections relevant to the task, including delegated investigations; do not eagerly load the whole documentation tree. Paths below are relative to the repository root.

| Work type | Relevant documentation |
| --- | --- |
| Product scope and domain behavior | `docs/product/scope-v1.md`, `docs/domain/domain-model.md`; `docs/product/scope-v0.8.0.md` for the commercial extension |
| Architecture and module boundaries | `docs/architecture/architecture.md`, `docs/architecture/backend-structure.md`, `docs/architecture/dependencies.md`; relevant ADRs indexed by `docs/architecture/decisions/README.md` |
| Database and Flyway | `docs/architecture/data-model.md`, `docs/architecture/decisions/0004-use-postgresql-and-flyway.md`; `docs/operations/postgresql-backup-restore.md` for backup, restore, or rollback work |
| Inventory, FEFO, and movements | `docs/domain/domain-model.md`, `docs/inventory/fefo-allocation.md`, `docs/inventory/movement-history.md`, `docs/architecture/inventory-package-structure.md`; `docs/architecture/decisions/0005-materialize-batch-balance.md` and `docs/architecture/decisions/0008-use-pessimistic-locking-for-stock-movements.md` for balance or locking work |
| Sales and customers | `docs/product/scope-v0.8.0.md`, `docs/product/customer-contacts.md`, `docs/product/customer-orders.md`, `docs/architecture/decisions/0012-define-v0.8-commercial-boundaries.md`; commercial sections of the domain and data models |
| Git, commits, and PRs | `CONTRIBUTING.md`, `docs/development/git-workflow.md`, `docs/development/commit-conventions.md`, `docs/development/issue-closing.md`; `docs/development/branch-protection.md` for required checks or protection work |
| Releases | Release and versioning sections of `docs/development/git-workflow.md`, the target release's spec in `docs/specs/` and readiness document in `docs/operations/` when present; `docs/operations/local-go-live-runbook.md` for operational rollout |
| Project Codex configuration | `docs/development/codex-subagents.md` |

## Architecture and scope

- Build a modular monolith, organized by feature/domain rather than global technical layers.
- Respect Spring Modulith module boundaries and communicate with other modules only through their public APIs; never import another module's internal infrastructure.
- Keep the V1 boundary historically accurate: customer, order, and sales behavior belongs to the explicit v0.8.0 scope and does not retroactively expand V1.
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
