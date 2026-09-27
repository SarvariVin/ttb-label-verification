# ADR-0004: Local-first OCR with optional cloud AI behind a pipeline interface

- **Status:** Accepted
- **Date:** 2026-09-23

## Context

Label data may be sensitive pre-approval business information; some deployments cannot send images to external services. Cloud OCR/LLMs give better accuracy and word geometry but add cost, latency and authorization (FedRAMP) questions.

## Decision

Define `ExtractionPipeline` with two implementations:
- **Local (default):** Tesseract 5 via Tess4J, plus deterministic OCR text search for each declared value.
- **Cloud (opt-in):** Google Cloud Vision OCR → OpenAI structured-output classification → bounding boxes from OCR word geometry.

The cloud pipeline is enabled only when both API keys are configured. A runtime setting selects the pipeline.

## Consequences

- Works air-gapped with zero API keys and zero per-label cost.
- Local mode has no bounding boxes or image-type classification and needs legible (≈1000 px+) images.
- New providers (e.g., an authorized government cloud model) are one new class.

## Alternatives considered

- Cloud-only: blocks restricted deployments.
- Local LLM: heavy hardware requirements for marginal gain over rule-based search.
