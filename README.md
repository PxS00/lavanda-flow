# Lavanda Flow

Lavanda Flow is the inventory and production management system for Céu de Lavanda.

The V1 scope focuses on controlled inventory: catalog items, suppliers, batches, stock movements, expiration dates, FEFO allocation, and operational alerts. Formulas, automated production, cost calculation, and complete traceability remain later evolutions and are not part of the initial implementation scope.

## Repository layout

```text
lavanda-flow/
├── .github/              # Issue/PR templates, ownership and CI workflows
├── backend/              # Java 25 + Spring Boot modular monolith
├── frontend/             # Angular 22 web application
├── docs/                 # Product, domain, architecture and development docs
├── compose.yaml          # Local PostgreSQL development environment
├── AGENTS.md             # Repository rules for engineering agents
├── CONTRIBUTING.md       # Contribution and Git workflow policy
├── SECURITY.md           # Security reporting policy
└── README.md
```

Infrastructure required for local Foundation development is intentionally kept small. PostgreSQL is defined in the root `compose.yaml`; production infrastructure is not coupled to Spring Boot Docker Compose lifecycle support.

## Technology baseline

### Frontend

- Angular 22
- TypeScript
- Angular Material / CDK
- Vitest + jsdom
- angular-eslint

### Backend

- Java 25 LTS
- Spring Boot 4.1
- Spring Modulith
- Spring Web MVC
- Spring Validation
- Spring Security
- Spring Data JPA
- Flyway
- Springdoc OpenAPI
- Actuator + Micrometer Prometheus

### Data and infrastructure

- PostgreSQL
- Docker / Docker Compose
- GitHub Actions

## Local development

Required tool versions are recorded in `.tool-versions`.

Start PostgreSQL from the repository root:

```bash
docker compose up -d
```

Run the backend:

```bash
cd backend
./mvnw spring-boot:run
```

Run the frontend in another terminal:

```bash
cd frontend
pnpm install --frozen-lockfile
pnpm start
```

Stop the local database when it is no longer needed:

```bash
docker compose down
```

## Validation

Backend:

```bash
cd backend
./mvnw verify
```

Frontend:

```bash
cd frontend
pnpm lint
pnpm exec ng test --watch=false
pnpm build
```

## Architecture

The backend is a modular monolith organized by feature/domain under `com.ceudelavanda.lavandaflow`.

Initial modules:

- `catalog`
- `inventory`
- `suppliers`
- `shared`

Business rules stay outside controllers, module internals are not imported across boundaries, PostgreSQL is the source of truth, and Flyway owns controlled schema evolution.

## Documentation

Start with:

- [`AGENTS.md`](AGENTS.md)
- [`CONTRIBUTING.md`](CONTRIBUTING.md)
- [`docs/product/scope-v1.md`](docs/product/scope-v1.md)
- [`docs/domain/domain-model.md`](docs/domain/domain-model.md)
- [`docs/architecture/architecture.md`](docs/architecture/architecture.md)
- [`docs/architecture/data-model.md`](docs/architecture/data-model.md)
- [`docs/architecture/backend-structure.md`](docs/architecture/backend-structure.md)
- [`docs/architecture/dependencies.md`](docs/architecture/dependencies.md)
- [`docs/development/local-environment.md`](docs/development/local-environment.md)
- [`docs/development/git-workflow.md`](docs/development/git-workflow.md)

## Development workflow

Issue-driven work starts from `develop` using branches named:

```text
features/<issue-number>/<short-description>
```

Feature pull requests target `develop` and are squash merged. `main` is production-only once issue-driven development is active.

## Status

Foundation bootstrap is in progress. Product functionality should be implemented only through its approved issue and milestone scope.
