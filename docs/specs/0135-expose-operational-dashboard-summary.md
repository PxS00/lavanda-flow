# Issue #135 — Expose operational dashboard summary

## Objective

Expose one backend-authoritative V1 dashboard summary for the Angular home screen without making the frontend recompute inventory rules.

This issue is backend-only. It adds a read-only inventory endpoint and the smallest supporting read composition required to produce the five approved operational counters.

## Source of truth

Apply, in order:

1. GitHub issue #135;
2. `AGENTS.md`;
3. `backend/AGENTS.md`;
4. `docs/product/scope-v1.md`;
5. current inventory/catalog public contracts and existing low-stock, available-stock, item-overview, and expiration-alert behavior;
6. this specification for the implementation-specific delta of #135.

Do not change frontend code or implement CSV import behavior.

## Existing semantics to preserve

The dashboard must compose existing backend-authoritative semantics rather than define parallel rules.

### Available stock

Reuse `AvailableStockQuery` semantics:

- include only positive batch balances;
- a batch without expiration is available;
- an expiring batch is available only when `expiresAt > asOfDate`;
- therefore `expiresAt <= asOfDate` is not available stock;
- a missing balance for an item is treated as zero by the caller.

### Low stock

Preserve current low-stock behavior from `GetLowStockAlerts`:

- only active catalog items participate;
- a minimum stock level must exist;
- compare backend-authoritative available stock against the configured minimum;
- low stock means `availableQuantity < minimumQuantity`;
- zero available stock with a configured positive minimum is also low stock;
- inactive items do not generate low-stock status.

The dashboard may reuse the existing low-stock application service or the same underlying read ports, but it must not implement a second divergent low-stock policy.

### Expiration

Preserve current expiration-alert behavior from `GetExpirationAlerts`:

- only positive-balance batches with an expiration date participate;
- `expiresAt <= asOfDate` is expired;
- `expiresAt > asOfDate && expiresAt <= cutoff` is expiring soon;
- batches outside the cutoff are neither expired nor expiring soon for this summary;
- zero-balance batches and batches without expiration are excluded.

Use the same configured expiration window currently exposed by `InventoryAlertProperties`. Do not introduce a dashboard-specific window or hard-coded replacement.

### Clock

Resolve the business date using the application `Clock`. All counters in one response must represent the same `asOfDate`.

## HTTP contract

Add:

```text
GET /api/v1/inventory/dashboard
```

No request body and no dashboard-specific query parameters are required for V1.

Return one stable DTO with these fields:

```text
asOfDate
expirationWindowDays
activeItemCount
lowStockItemCount
outOfStockItemCount
expiringSoonBatchCount
expiredBatchCount
```

Use integral JSON values for counters. Do not expose persistence types, `Page`, `Pageable`, entities, or internal query objects.

### Counter semantics

`activeItemCount`
- number of active catalog inventory items.

`lowStockItemCount`
- number of active items that satisfy the existing low-stock semantics above.

`outOfStockItemCount`
- number of active catalog items whose backend-authoritative available quantity is exactly zero at `asOfDate`;
- inactive catalog items are excluded from this operational counter;
- items with only expired/expiring-today positive physical balance count as out of stock because that balance is not available;
- items with no batches also count as out of stock when active.

`expiredBatchCount`
- number of positive-balance batches with non-null expiration where `expiresAt <= asOfDate`.

`expiringSoonBatchCount`
- number of positive-balance batches where `asOfDate < expiresAt <= asOfDate + expirationWindowDays`.

`expirationWindowDays`
- the same configured non-negative window used by the existing expiration-alert endpoint.

The response metadata must make the date/window semantics explicit enough for #137 to render the counters without recomputation.

## Application/read composition

Keep ownership in the `inventory` module.

The implementation should add a focused dashboard application use case/read model under the existing inventory package-by-feature structure.

Prefer composition of existing read capabilities:

- catalog public lookup contract for catalog activation state;
- `AvailableStockQuery` for available balances;
- existing low-stock behavior/read path;
- existing expiration-alert behavior/read path;
- application `Clock`;
- existing `InventoryAlertProperties` for the configured window at the web/configuration boundary.

Do not call catalog infrastructure, JPA repositories, or entities from Inventory.

The current public catalog lookup does not enumerate all items. If #135 requires catalog enumeration, extend an existing published catalog query contract with only the smallest read operation needed. Keep immutable snapshot values across the module boundary and do not expose catalog repositories or JPA types.

Avoid N+1 per-item queries. Active catalog items and available balances should be obtained in bulk/read-oriented form.

Do not create a persisted dashboard table, cache, analytics projection, event pipeline, scheduled refresh, or mutable dashboard state.

## Controller and OpenAPI

Keep the controller thin:

- resolve only web/configuration inputs such as the existing expiration window;
- delegate all counting/read composition to the application layer;
- map the application result to the HTTP response DTO.

OpenAPI must document:

- all five counters;
- item-based versus batch-based semantics;
- `asOfDate`;
- `expirationWindowDays` and inclusive cutoff behavior;
- that unavailable expired/expiring-today stock is not treated as available for out-of-stock/low-stock semantics.

Do not add business-rule calculations to OpenAPI mapper/controller code.

## Persistence

No new persisted state is required.

- no Flyway migration;
- no dashboard table;
- no schema change solely for counters;
- reuse current PostgreSQL-backed queries/repositories/read ports.

If a new inventory query adapter is genuinely needed for efficient counting, keep it infrastructure-internal and return application values only.

## Testing

Add focused tests covering the behavior owned by #135.

### Application tests

Use a fixed application `Clock` and verify at minimum:

- active versus inactive catalog items;
- active item with no available balance counts as out of stock;
- available stock prevents out-of-stock count;
- stock that expires on `asOfDate` is unavailable and can make an item out of stock;
- low-stock count matches existing minimum-stock semantics;
- inactive items do not contribute low-stock/out-of-stock operational counts;
- zero/minimum edge cases preserve current low-stock behavior;
- expiration window metadata is preserved;
- expired and expiring-soon counters are separated correctly.

### PostgreSQL/Testcontainers integration

Cover a mixed scenario with:

- active and inactive items;
- item with no batches;
- available non-expired stock;
- item backed only by expired stock;
- configured minimum below/equal/above available stock;
- expired batch;
- batch expiring today;
- future batch inside the configured window;
- future batch outside the configured window;
- positive batch without expiration;
- zero-balance batch.

Assert the resulting five counters and window/date metadata against persisted PostgreSQL state.

Do not use H2.

### Web contract

Verify:

- `GET /api/v1/inventory/dashboard`;
- exact stable response field names;
- success status and integral counter values;
- configured expiration window is passed through;
- no JPA/Spring Data type appears in the contract.

Existing Spring Modulith verification must remain green.

## Acceptance criteria

- [ ] `GET /api/v1/inventory/dashboard` returns one stable read-only summary DTO.
- [ ] `activeItemCount` uses catalog active-state semantics.
- [ ] `lowStockItemCount` reuses the current active/minimum/available-stock semantics.
- [ ] `outOfStockItemCount` counts active items with zero backend-authoritative available stock, including items whose only physical balance is already ineligible by expiration.
- [ ] `expiredBatchCount` counts positive-balance batches with `expiresAt <= asOfDate`.
- [ ] `expiringSoonBatchCount` uses the existing configured inclusive expiration-alert cutoff.
- [ ] `asOfDate` comes from the application `Clock` and is consistent across the response.
- [ ] `expirationWindowDays` exposes the same existing alert-window configuration used by inventory alerts.
- [ ] No inventory/expiration/FEFO rule is duplicated in the controller or frontend.
- [ ] Catalog data crosses the module boundary only through a published contract and immutable values.
- [ ] No per-item N+1, full movement-history loading, graph aggregation, mutable dashboard persistence, or analytics infrastructure is introduced.
- [ ] No Flyway/schema change is introduced solely for #135.
- [ ] OpenAPI documents every counter and date/window semantic.
- [ ] PostgreSQL/Testcontainers coverage verifies the mixed operational scenario.
- [ ] Spring Modulith boundaries remain valid.
- [ ] `./mvnw verify` succeeds.

## Out of scope

- frontend dashboard implementation (#137);
- initial CSV import (#136);
- charts, trends, forecasts, historical analytics, purchasing recommendations, or notifications;
- per-category dashboard breakdowns;
- minimum-shelf-life policy from #73;
- new dependencies or analytics/cache infrastructure;
- changes to production-module behavior.

## Final validation

Before finishing implementation:

1. run `./mvnw verify`;
2. review the complete `git diff` for scope and module-boundary violations;
3. run `git diff --check`;
4. run `git status --short`.
