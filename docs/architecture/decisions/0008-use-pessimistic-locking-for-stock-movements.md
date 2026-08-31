# ADR 0008 — Use pessimistic locking for stock movements

- **Status:** Accepted
- **Date:** 2026-08-27
- **Updated:** 2026-08-31

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

The original decision locked the existing batch rows for FEFO. That protects
writers over rows that already exist but does not protect the predicate “all
batches for this inventory item”. Under PostgreSQL `READ COMMITTED`, a stock
receipt can insert a new batch after FEFO has acquired its current batch lock
set. Once stock receipt became a real transactional workflow in v0.3.0, that
predicate gap became a reachable production race.

## Decision

Stock-changing use cases continue to use **pessimistic write locking** for the
batches whose balances can be mutated.

The domain repository port exposes lock semantics without depending on JPA:

- `lockByIdForUpdate(UUID)` protects one explicit batch;
- `lockByInventoryItemIdForUpdate(UUID)` protects all existing batches of one
  inventory item before FEFO planning.

The JPA adapter implements those operations with
`LockModeType.PESSIMISTIC_WRITE`. Ordinary read methods remain unlocked.

### Item-level serialization for FEFO and stock receipt

FEFO withdrawal and stock receipt additionally acquire a transaction-scoped
pessimistic write lock on the catalog-owned `inventory_item` row before either
operation reads, locks, or inserts inventory batches.

The catalog publishes this synchronization point through
`InventoryItemOperationLock`. Its implementation uses a JPA
`PESSIMISTIC_WRITE` lookup and returns the fresh public inventory-item snapshot
while the row lock is held. Inventory therefore does not access catalog
repositories or persistence entities directly.

The lock acquisition order is deterministic:

1. acquire the `inventory_item` lock;
2. for FEFO, acquire existing batch row locks in immutable UUID order;
3. perform FEFO allocation or stock-receipt insertion;
4. persist balance changes and immutable movements;
5. release every lock on transaction commit or rollback.

Stock receipt does not lock existing batch rows because it only inserts a new
batch. It shares the item-level serialization point with FEFO so the two
operations cannot overlap while FEFO is deciding which batches exist.

If stock receipt acquires the item lock first, FEFO waits. After receipt commits,
FEFO acquires the lock and, under `READ COMMITTED`, its following batch query
sees the newly committed batch. If FEFO acquires the item lock first, receipt
waits until the FEFO allocation and movements commit, then inserts the new batch.
Both schedules are therefore equivalent to a deterministic serial order.

For FEFO, batch rows remain locked in immutable UUID order before the pure domain
policy applies its business ordering. This intentionally separates **lock
acquisition order** from **FEFO allocation order** and reduces deadlock risk.

There is no automatic application retry in V1. A competing operation waits for
the conflicting transaction and then continues against fresh committed state.
Database-level lock timeout or deadlock failures remain infrastructure failures;
a retry policy will only be added if production evidence justifies a concrete
contract.

## Consequences

### Positive

- prevents lost updates on materialized batch balances;
- prevents stale balance validation from authorizing conflicting withdrawals;
- closes the FEFO predicate gap created by concurrent batch insertion;
- keeps receipt batch creation and initial `ENTRY` history atomic;
- keeps FEFO balance changes and consumption history atomic;
- keeps catalog persistence internals behind a published module contract;
- avoids advisory-lock key hashing and collision semantics;
- avoids a dedicated lock table or new migration;
- gives FEFO deterministic item-then-batch lock acquisition.

### Trade-offs

- concurrent FEFO and stock receipts for the same inventory item are serialized;
- concurrent receipts for the same inventory item are also serialized;
- FEFO takes a deliberately coarse item lock plus locks over existing batches;
- lock contention can increase response time under heavy write load.

This is acceptable for Lavanda Flow because inventory writes are short,
contention is expected to be low, and stock correctness has priority over
maximum write throughput.

## Alternatives considered

### PostgreSQL advisory locking

Rejected for V1. Advisory locks would avoid coupling serialization to a table
row, but UUID item identifiers would need a deterministic advisory key mapping.
That introduces hash/key-collision semantics and a second locking convention
without providing a material benefit over the existing catalog row.

### Dedicated inventory lock row

Rejected. A separate lock table would make the synchronization point explicit
but would add schema, lifecycle, and consistency overhead for a row that already
has a stable catalog identity.

### Optimistic locking

Rejected for V1. `@Version` would require carrying persistence version state
through the current entity-to-domain mapping or redesigning the adapter around
managed entities. More importantly, a FEFO operation can update several rows;
one optimistic conflict at flush time would invalidate the entire allocation
plan and require explicit whole-command retry semantics.

### Database constraint only

Rejected. The existing non-negative constraint is useful defense in depth but
cannot detect a lost update or a FEFO allocation made from an incomplete batch
set.

### Serializable isolation for every stock transaction

Rejected. It is broader than the invariant being protected and would introduce
transaction-level serialization failures and retry requirements where targeted
row locks provide a deterministic wait contract.
