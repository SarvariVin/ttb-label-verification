# ADR-0010: Pipeline fallback in both directions, not on timeout

- **Status:** Accepted
- **Date:** 2026-09-23

## Context

Either pipeline can fail (missing native library, expired key, quota, network).

## Decision

If the selected pipeline fails, try the other one when it is available: local → cloud (if configured), cloud → local. **Do not** fall back after a timeout. The stored `model_used` always records which pipeline actually ran.

## Consequences

- Transient failures rarely reach the applicant.
- A timeout does not double the wait; the label is saved `PENDING` for re-analysis.

## Alternatives considered

- No fallback: more `PENDING` labels.
- Fallback on timeout: up to 2× latency.
