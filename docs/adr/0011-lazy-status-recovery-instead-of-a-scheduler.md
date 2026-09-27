# ADR-0011: Lazy status recovery instead of a scheduler

- **Status:** Accepted
- **Date:** 2026-09-23

## Context

Correction deadlines expire (7 days conditional, 30 days needs-correction), and a crashed analysis can leave labels in `PROCESSING`. A scheduler adds infrastructure and cluster coordination.

## Decision

Compute the **effective status** on every read (`EffectiveStatus`) and persist the transition when it differs:
- `PROCESSING` older than 5 minutes → `PENDING_REVIEW`;
- `NEEDS_CORRECTION` past deadline → `REJECTED`;
- `CONDITIONALLY_APPROVED` past deadline → `NEEDS_CORRECTION` with a **fresh 30-day window** (prevents a double downgrade on consecutive reads).

## Consequences

- No scheduler; always-correct display.
- Stored status lags until a label is read; reports must use effective status (or a nightly reconciliation job in production).

## Alternatives considered

- Cron/Quartz job: extra moving part, clustering concerns.
