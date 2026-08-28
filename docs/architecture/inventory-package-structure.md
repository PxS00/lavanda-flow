# Inventory package structure

## Purpose

The `inventory` bounded module remains one Spring Modulith application module organized by architectural layer. Inside the application layer, related orchestration and read models are grouped by business capability so new operational workflows can grow without returning to generic `command`, `query`, or `result` buckets.

## Target structure

```text
inventory/
├── domain/
│   ├── exception/
│   └── ... inventory domain types
├── application/
│   ├── alerts/
│   ├── fefo/
│   ├── history/
│   ├── minimumstock/
│   ├── movement/
│   └── stock/
└── infrastructure/
    ├── config/
    ├── persistence/
    └── web/
        ├── request/
        └── response/
```

The infrastructure layer stays primarily grouped by technical boundary because persistence and HTTP are already cohesive adapter boundaries. HTTP responsibilities are expressed through focused controllers rather than one inventory-wide controller.

## Capability ownership

- `application.stock`: current/available stock queries and their framework-neutral models.
- `application.movement`: explicit batch entry, withdrawal, and adjustment use cases.
- `application.fefo`: automatic FEFO withdrawal orchestration and result models.
- `application.alerts`: expiration and low-stock alert queries.
- `application.minimumstock`: minimum-stock configuration lifecycle.
- `application.history`: movement-history query port, filters, projections, and results.

## Constraints

- `inventory` remains one Spring Modulith module.
- Domain types remain framework and persistence agnostic.
- Application ports must not expose JPA or Spring Data types.
- JPA entities and Spring Data repositories remain in infrastructure.
- Existing public HTTP routes and business behavior are preserved by this organization refactor.
- Packages are introduced only where they group a meaningful capability; domain and persistence are not fragmented solely to mirror taxonomy.
