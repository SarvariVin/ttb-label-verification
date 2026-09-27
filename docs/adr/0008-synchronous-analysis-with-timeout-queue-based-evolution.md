# ADR-0008: Synchronous analysis with timeout; queue-based evolution

- **Status:** Accepted
- **Date:** 2026-09-23

## Context

Applicants expect immediate feedback. Analysis takes ~0.5–1 s locally and ~2–5 s in the cloud, with rare slow outliers.

## Decision

Run analysis inside the submit request on a virtual thread with a hard timeout (`app.pipeline.timeout`, default 60 s). Parallel cloud OCR also uses virtual threads. The target design for high volume — outbox + queue + workers, `202 Accepted` — is documented in [infrastructure.md](../infrastructure.md#6-scaling-target) and diagram 14.

## Consequences

- Simple, instant feedback, no broker to operate today.
- A slow pipeline occupies a request thread; timeouts leave the label `PENDING` for re-analysis.

## Alternatives considered

- Queue from day one: extra infrastructure before the volume needs it.
