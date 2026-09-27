# ADR-0009: Database transactions never wrap AI calls

- **Status:** Accepted
- **Date:** 2026-09-23

## Context

OCR and LLM calls can take seconds. Holding a pooled connection and row locks for that long exhausts the pool under load.

## Decision

`LabelAnalysisService` uses three short transactions via `TransactionTemplate`: create the label, load inputs, persist results. OCR and LLM calls run with no transaction open.

## Consequences

- Pool stays small under load.
- A crash between transactions leaves a label `PROCESSING`; [ADR-0011](0011-lazy-status-recovery-instead-of-a-scheduler.md) recovers it.

## Alternatives considered

- `@Transactional` on the whole use case: simplest code, worst resource behavior.
