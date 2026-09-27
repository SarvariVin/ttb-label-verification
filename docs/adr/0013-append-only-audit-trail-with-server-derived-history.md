# ADR-0013: Append-only audit trail with server-derived history

- **Status:** Accepted
- **Date:** 2026-09-23

## Context

Specialist decisions must be reconstructable. Client-supplied "previous state" cannot be trusted.

## Decision

- `human_reviews` (field-level) and `status_overrides` (label-level) are insert-only; no `updated_at`.
- The original status recorded in a review is read from the database, never from the request.
- Every analysis run keeps its raw output, model, timings and token usage; re-analysis supersedes but never deletes.

## Consequences

- Full decision history per label.
- Not tamper-evident yet (production: WORM export or hash chaining).

## Alternatives considered

- Mutable history columns on the label: loses the trail.
