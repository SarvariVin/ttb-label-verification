# ADR-0016: Synthetic sample labels

- **Status:** Accepted
- **Date:** 2026-09-23

## Context

Demos, documentation and benchmarks need label images with known field values. Real product labels carry trademark and ownership concerns and are often too low-resolution for local OCR.

## Decision

Generate fictional labels with `scripts/SampleLabelGenerator.java` (Java2D, no dependencies): invented brands and addresses, 1600×2000 px, bold capitalized warning prefix, plus one deliberately non-compliant label. Each ships with `application.json` and a batch CSV.

## Consequences

- No third-party assets in the repository; reproducible.
- Synthetic labels are easier than real photography; accuracy on real labels must be measured separately ([production.md](../production.md#5-ai-pipeline)).

## Alternatives considered

- Real label photographs: ownership questions, inconsistent quality.
