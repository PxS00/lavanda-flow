# Database Migrations

Flyway is the source of truth for PostgreSQL schema evolution in Lavanda Flow.

## Location

Versioned migrations live in this directory:

```text
backend/src/main/resources/db/migration/
```

## Naming

Use Flyway versioned migration names:

```text
V<version>__<short_description>.sql
```

Examples:

```text
V1__baseline.sql
V2__create_inventory_item.sql
V3__create_supplier.sql
```

Rules:

- never edit an applied versioned migration to change production history;
- add a new migration for every forward schema change;
- keep descriptions in lowercase snake case;
- keep migrations deterministic and compatible with PostgreSQL;
- do not use Hibernate schema generation as a replacement for Flyway;
- include database constraints for important invariants when the domain model requires them.

The Foundation baseline intentionally contains no business tables. Inventory Core migrations must be introduced only by the issues that own those domain changes.
