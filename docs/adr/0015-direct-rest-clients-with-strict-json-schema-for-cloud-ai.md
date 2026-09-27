# ADR-0015: Direct REST clients with strict JSON schema for cloud AI

- **Status:** Accepted
- **Date:** 2026-09-23

## Context

The cloud pipeline needs one OCR call and one classification call. AI frameworks add large dependency trees and hide the exact request.

## Decision

Use Spring `RestClient` directly: Google Vision `images:annotate` and OpenAI Chat Completions with `response_format: json_schema, strict: true` (every property required; nullables typed `["string","null"]`). Bounding boxes come from OCR word geometry, never from the LLM.

## Consequences

- Small dependency tree; requests are auditable.
- Provider switch = new `ExtractionPipeline` implementation.
- Google Vision uses an API key for simplicity; production uses workload identity.

## Alternatives considered

- Vendor SDKs / AI frameworks: heavier, less transparent.
