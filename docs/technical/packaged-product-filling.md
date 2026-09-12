# Packaged product filling

## Purpose

Lavanda Flow models a repeatable packaged presentation as its own `FINISHED_PRODUCT` inventory item. A packaged presentation is produced through the existing `production` module; filling does not introduce a separate module or stock model.

## Formula kinds

Production formulas persist one process kind:

- `STANDARD` — existing production behavior;
- `PACKAGED_FILLING` — filling/bottling a finalized bulk product into a separately stocked packaged presentation.

Requests that omit `kind` remain `STANDARD` for backward compatibility.

A `PACKAGED_FILLING` formula must produce a `FINISHED_PRODUCT` measured in `UNIT` and must include at least one non-`UNIT` `FINISHED_PRODUCT` ingredient representing formulated bulk stock. Other inventory-controlled packaging components remain normal formula ingredients in their own persisted units.

No automatic unit conversion is performed.

## Scaled requirements

The backend exposes a read-only requirements calculation:

```text
GET /api/v1/production/formulas/{formulaId}/requirements?outputQuantity={quantity}
```

The response returns the requested output quantity and the exact scaled quantity for each formula ingredient. This calculation uses the same exact-decimal scaling rule as production registration.

The requirements endpoint does not:

- reserve stock;
- choose source batches;
- validate definitive batch eligibility;
- reserve or allocate a lot sequence;
- mutate production or inventory state.

Angular displays this backend result and never reimplements the scaling rule.

## Lot allocation

Generated `STANDARD` outputs preserve the existing convention:

```text
TTT-EEE-LLL-MM-YYYY
```

Generated `PACKAGED_FILLING` outputs use:

```text
SSS-MM-YYYY
```

`SSS` is a backend-allocated global packaged-output sequence from `001` through `999` scoped only by calendar month/year. It resets for a new month/year. Fragrance, production type, presentation, and source lot do not create independent packaged sequences.

PostgreSQL persists packaged allocation state in `packaged_production_lot_sequence`. Allocation participates in the same production transaction, so a failed production does not consume a sequence value.

Manual lot mode remains available and does not allocate either generated sequence.

## Execution and genealogy

`POST /api/v1/production/executions` remains the production mutation contract. The request does not choose the generated lot format directly; the persisted formula kind determines which generated allocator applies.

One successful packaged filling execution:

1. validates the formula and exact source allocations;
2. consumes the exact required quantities through inventory's public production contract;
3. creates one output batch for the packaged presentation;
4. records the output stock entry and exact source consumptions;
5. stores the definitive packaged lot code;
6. preserves genealogy through explicit execution/consumption/output relationships.

One packaged output may consume multiple bulk batches and multiple packaging-component batches. The output lot string is never parsed to reconstruct origin.

## Expiration

Packaged-product expiration remains explicit in the production request. Filling does not automatically renew, copy, shorten, or derive expiration from source batches.

## Schema

Flyway `V16__support_packaged_product_filling.sql`:

- adds `production_formula.formula_kind`, backfilling existing rows to `STANDARD`;
- constrains formula kinds to `STANDARD` and `PACKAGED_FILLING`;
- adds the monthly `packaged_production_lot_sequence` table.

The existing `production_lot_sequence` table and `TTT-EEE-LLL-MM-YYYY` semantics are unchanged.
