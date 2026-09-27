# ADR-0001: Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-09-23

## Context

Design choices in this system (security boundaries, data portability, AI behavior) have compliance consequences and must be explainable to reviewers, auditors and future maintainers.

## Decision

Record every significant decision as a short Architecture Decision Record (ADR) in `docs/adr/`, numbered sequentially, using [template.md](template.md). ADRs are immutable once accepted; a change of direction is a new ADR that supersedes the old one.

## Consequences

- Reviewers can trace why the system behaves as it does.
- Slight overhead per decision; kept low by the one-page template.

## Alternatives considered

- Wiki pages (drift from code, no review trail).
- Comments in code only (miss cross-cutting decisions).
