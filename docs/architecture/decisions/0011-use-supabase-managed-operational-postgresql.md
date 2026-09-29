# ADR 0011 — Use Supabase as managed operational PostgreSQL

- **Status:** Accepted
- **Date:** 2026-09-23
- **Related issue:** #254
- **Target release:** v0.7.1

## Context

Lavanda Flow uses PostgreSQL as its operational source of truth, Flyway as its schema authority, JPA/Hibernate for persistence, and Testcontainers PostgreSQL for integration testing. ADR 0004 established those choices.

ADR 0010 established the v0.6.0 local-first runtime: Angular and Spring Boot run on the operator-owned notebook, PostgreSQL also runs locally in Docker Compose, notebook/tablet access stays on the trusted LAN, and Spring Security provides stateful same-origin authentication.

That runtime removed recurring infrastructure cost and was appropriate for initial operation, but local PostgreSQL makes database availability, storage durability, and backup execution dependent on the operator notebook. The current requirement is to move the operational database to managed PostgreSQL while keeping the application architecture and operator workflow stable.

A Supabase project already exists in `sa-east-1` using standard PostgreSQL 17. The application does not require Supabase's frontend Data API, Auth, Realtime, Storage, Edge Functions, or schema-migration tooling.

## Decision

Use Supabase as the managed host for Lavanda Flow's **operational PostgreSQL database only**.

The runtime boundary becomes:

```text
browser / trusted-LAN tablet
            |
            v
operator-hosted Angular + Spring Boot
            |
            | JDBC over TLS
            v
Supabase managed PostgreSQL
```

### Application boundary

Spring Boot remains the only application backend used by Angular.

All existing application responsibilities remain unchanged:

- Spring Security authentication/session/CSRF;
- REST/JSON `/api/v1` contracts;
- transaction boundaries;
- inventory and production business rules;
- FEFO;
- expiration;
- audit history;
- production allocation and genealogy.

Angular does not connect directly to Supabase.

### Database and schema authority

PostgreSQL remains the source of truth.

Flyway remains the only application schema-evolution authority. Hibernate remains validation-only for controlled environments.

Existing Flyway migrations are immutable. Supabase dashboard DDL, `supabase db push`, Supabase migration files, or another parallel migration mechanism must not be used to own Lavanda Flow application schema.

Testcontainers PostgreSQL remains the integration-test database. Repository-managed Docker PostgreSQL remains the local development database.

### Connection strategy

Lavanda Flow's Spring Boot runtime is persistent.

Use:

- the Supabase **direct connection** on port 5432 when the operator host has working IPv6 connectivity; or
- Supavisor **session mode** on port 5432 when the runtime network is IPv4-only and direct access is unavailable.

Do not use transaction-pooling mode on port 6543 for the persistent JPA runtime. It is intended for short-lived/serverless clients and introduces prepared-statement and session-state constraints without a current requirement.

Flyway, `pg_dump`, restore, and other PostgreSQL-native maintenance operations should use direct PostgreSQL connectivity when reachable. If the operator network cannot reach the direct endpoint, the maintenance path must be explicitly validated before production cutover.

### Transport and secrets

Production PostgreSQL connections must use TLS with server certificate/identity verification where supported by the selected JDBC/native-tool path.

Database credentials and credential-bearing connection strings remain external to tracked source and image layers.

Supabase API keys are not application database credentials and are not introduced for this decision.

### Backup and recovery

Moving the database to a managed provider does not remove Lavanda Flow's independent backup requirement.

The operational baseline remains:

- PostgreSQL-native logical dump;
- SHA-256 integrity sidecar;
- copy outside the database-provider failure domain;
- tested disposable restore;
- protected pre-upgrade backups.

Provider-managed backups are supplementary. They do not replace the repository-owned recovery path.

### Relationship to ADR 0010

This ADR **partially supersedes ADR 0010** only for:

- production PostgreSQL placement;
- local operational PostgreSQL persistence;
- backup tooling assumptions tied to the local database container.

ADR 0010 remains accepted for:

- operator-hosted Angular + Spring Boot;
- trusted-LAN browser/tablet access;
- same-origin application delivery;
- Spring Security stateful authentication;
- CSRF;
- workstation/application lifecycle;
- application HTTP exposure.

A future public/cloud application deployment requires a separate decision.

## Consequences

### Positive

- the production database no longer depends on notebook storage durability;
- PostgreSQL remains standard and portable;
- domain/application modules require no Supabase-specific code;
- Flyway, JPA/Hibernate, and Testcontainers remain unchanged in ownership;
- local development does not require cloud database access;
- a future host migration can still use standard PostgreSQL tooling.

### Trade-offs and risks

- production database availability now depends on internet connectivity and the managed provider;
- the Free Plan can pause low-activity projects, so availability is not equivalent to an always-on paid database;
- datasource/TLS/connection-mode configuration becomes an operational responsibility;
- application connection-pool sizing must account for managed database limits and provider-owned connections;
- backup tooling must connect to a remote database rather than relying on the local Compose database container;
- provider account/project access becomes part of disaster-recovery governance.

## Alternatives considered

### Keep operational PostgreSQL only on the operator notebook

Rejected for #254. It preserves the current topology but does not satisfy the requirement to move the operational database to managed infrastructure.

### Use Supabase Data API directly from Angular

Rejected. It would create a second application/data-access boundary, duplicate or relocate authorization/business-rule concerns, and materially change the established Spring Boot architecture.

### Adopt Supabase Auth in the same change

Deferred. Identity architecture is separate from database hosting. Spring Security remains the accepted operator authentication boundary.

### Add PostgreSQL RLS for application-user or tenant authorization

Deferred. The current application is not multi-tenant and Spring Boot owns authorization. RLS may be reconsidered with a concrete tenant/isolation requirement.

### Use Supabase transaction pooler for the Spring Boot runtime

Rejected. The application is a persistent JPA backend; transaction mode is optimized for short-lived/serverless clients and adds prepared-statement/session-state constraints.

### Replace Flyway with Supabase migrations

Rejected. It would create competing schema ownership and violate ADR 0004 and existing release/test workflows.

### Require Supabase for local development and tests

Rejected. Local Docker PostgreSQL and Testcontainers provide deterministic development/testing without network/provider dependency.

### Migrate the complete application to public cloud hosting

Out of scope. #254 changes database hosting only. Application hosting and public-network security require a separate decision.

## Operational notes

Current Supabase documentation should be checked again before each production cutover because hosted connection modes, plan limits, backup capabilities, and lifecycle behavior can change.

Relevant provider references:

- https://supabase.com/docs/guides/database/connecting-to-postgres
- https://supabase.com/docs/guides/database/connecting-to-postgres/pooling-and-limits
- https://supabase.com/docs/guides/platform/free-project-pausing
- https://supabase.com/docs/guides/deployment/going-into-prod
