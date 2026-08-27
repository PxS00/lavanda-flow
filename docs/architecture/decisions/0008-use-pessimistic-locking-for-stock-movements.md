# ADR 0008 — Use pessimistic locking for stock movements

- **Status:** Accepted
- **Date:** 2026-08-27

## Context

`inventory_batch.current_quantity` is a materialized stock balance. The domain
aggregate prevents an individual operation from reducing that balance below
zero, and PostgreSQL also has a `CHECK (current_quantity >= 0)` constraint.
Those protections are insufficient when two transactions read the same balance
before either one commits: both can validate successfully and a later update can
overwrite the earlier one, producing a lost update while the stored value still
satisfies the database constraint.

Stock entry, explicit withdrawal, stock adjustment, and FEFO withdrawal all
mutate existing batch balances. FEFO is additionally a multi-row operation: it
plans allocations across all eligible batches and persists one movement per
allocated batch in the same transaction.

## Decision

Stock-changing use cases will use **pessimistic write locking** for the batches
whose balances can be mutated.

The domain repository port exposes lock semantics without depending on JPA:

- `lockByIdForUpdate(UUID)` protects one explicit batch;
- `lockByInventoryItemIdForUpdate(UUID)` protects all existing batches of one
  inventory item before FEFO planning.

The JPA adapter implements those operations with
`LockModeType.PESSIMISTIC_WRITE`. Ordinary read methods remain unlocked.

For FEFO, rows are locked in immutable UUID order before the pure domain policy
applies its business ordering. This intentionally separates **lock acquisition
order** from **FEFO allocation order** and reduces deadlock risk between
concurrent multi-batch operations.

The lock is held by the existing application transaction until commit or
rollback. Balance persistence and movement persistence therefore remain atomic.
A competing writer waits for the lock, then evaluates the domain rules against
the latest committed balance.

There is no automatic application retry in V1. After waiting for a conflicting
transaction, a command either succeeds against the fresh state or fails through
its existing business exception, such as insufficient stock. Database-level
lock timeout or deadlock failures remain infrastructure failures and can be
addressed by a future retry policy if production evidence justifies it.

## Consequences

### Positive

- prevents lost updates on materialized batch balances;
- prevents stale balance validation from authorizing conflicting withdrawals;
- keeps balance changes and audit movements consistent in one transaction;
- does not leak JPA lock types into the domain or application layers;
- avoids adding a persistence version field to the domain model;
- gives FEFO deterministic multi-row lock acquisition.

### Trade-offs

- concurrent writes to the same batch are serialized;
- FEFO takes a deliberately coarse lock over all existing batches for the item,
  so an adjustment to any of those batches waits while FEFO completes;
- lock contention can increase response time under heavy write load.

This is acceptable for Lavanda Flow because inventory writes are short,
contention is expected to be low, and stock correctness has priority over
maximum write throughput.

## Alternatives considered

### Optimistic locking

Rejected for V1. `@Version` would require carrying persistence version state
through the current entity-to-domain mapping or redesigning the adapter around
managed entities. More importantly, a FEFO operation can update several rows;
one optimistic conflict at flush time would invalidate the entire allocation
plan and require explicit whole-command retry semantics. That complexity is not
justified by the expected workload.

### Database constraint only

Rejected. The existing non-negative constraint is useful defense in depth but
cannot detect a lost update that overwrites another valid non-negative balance.

### Serializable isolation for every stock transaction

Rejected. It is broader than the invariant being protected and would introduce
transaction-level serialization failures and retry requirements where targeted
row locks are sufficient.
