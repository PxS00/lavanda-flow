# Repository Metadata

Lavanda Flow uses a compact label taxonomy and release milestones to keep issue-driven development consistent without duplicating information already encoded by branches, pull requests, and milestones.

## Label taxonomy

### Type

Use one primary type label per issue:

- `type: feature` — new product functionality or capability;
- `type: bug` — defect correction;
- `type: chore` — maintenance, configuration, or repository work;
- `type: test` — test coverage or testing infrastructure;
- `type: docs` — documentation-only work;
- `type: ci` — continuous integration or delivery work.

### Area

Use the smallest set of area labels that accurately identifies ownership:

- `area: backend`
- `area: frontend`
- `area: database`
- `area: architecture`
- `area: api`
- `area: observability`
- `area: security`
- `area: repository`

Prefer one area label. Use more than one only when the issue genuinely crosses ownership boundaries.

### Priority

Use one priority label:

- `priority: p0` — critical work that blocks the planned release or an essential invariant;
- `priority: p1` — high-priority work expected in the current release plan;
- `priority: p2` — normal-priority work that can be sequenced after higher-impact items.

Priority expresses delivery urgency, not issue type or complexity.

### Status

Status labels are exceptional and should be removed when no longer applicable:

- `status: blocked` — progress depends on an unresolved prerequisite;
- `status: needs-decision` — implementation is waiting for an explicit product or architectural decision.

Do not create labels that duplicate the normal GitHub issue state or milestone membership.

## Milestones

### `v0.1.0 — Foundation`

Owns the repository, backend, frontend, database, observability, API documentation, CI, and governance foundation required before Inventory Core development proceeds.

Foundation issues belong to this milestone, including repository protection work that becomes actionable after the CI workflow names are stable.

### `v0.2.0 — Inventory Core`

Owns the first product implementation for catalog items, suppliers, batches, stock movements, inventory operations, FEFO, alerts, queries, and the related reliability tests.

Product issues must not be moved into Foundation merely because they depend on Foundation infrastructure.

## Usage rules

For a normal issue:

1. assign the issue to the release milestone that owns its delivery;
2. apply exactly one primary `type:*` label;
3. apply the minimum useful `area:*` label set;
4. apply exactly one `priority:*` label;
5. add a `status:*` label only while the exceptional state is true;
6. keep dependencies in the issue body instead of encoding dependency graphs as labels.

Milestones answer **when the work is delivered**. Labels answer **what kind of work it is, where it belongs, and its delivery priority**.

## Current repository plan

The initial taxonomy and the `v0.1.0 — Foundation` and `v0.2.0 — Inventory Core` milestones are the approved repository organization for the current roadmap. New labels or milestones should be introduced only when they represent a durable distinction that is not already captured by the existing model.
