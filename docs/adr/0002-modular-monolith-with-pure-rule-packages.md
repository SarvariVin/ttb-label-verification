# ADR-0002: Modular monolith with pure rule packages

- **Status:** Accepted
- **Date:** 2026-09-23

## Context

Label verification combines stable infrastructure (web, persistence, security) with regulatory rules that change whenever TTB updates 27 CFR. Rules need fast, isolated tests; the team is small; operational simplicity matters more than independent scaling of every part.

## Decision

Build one deployable Spring Boot application organized as layered packages: `web → service → (ai, labels, regulatory) → domain / repository / storage`. The `regulatory`, `labels` and `ai.compare` packages are **plain Java with no Spring or JPA imports** — static functions and records only.

## Consequences

- 55+ rule tests run in milliseconds without a Spring context.
- Regulatory changes touch `regulatory/*` only.
- One artifact to build, secure and deploy.
- Horizontal scaling is whole-application until analysis moves to workers ([ADR-0008](0008-synchronous-analysis-with-timeout-queue-based-evolution.md)).

## Alternatives considered

- Microservices (OCR service, review service): more network, auth and deployment surface than the domain justifies today.
- Rules embedded in services: harder to test and to audit against the CFR.
