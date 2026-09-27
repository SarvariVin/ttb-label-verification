# ADR-0007: Authorization at route, method and data layers

- **Status:** Accepted
- **Date:** 2026-09-23

## Context

Two roles with very different rights; applicants from competing companies share the system. A missed check on any new endpoint would leak confidential labels.

## Decision

Enforce authorization three times:
1. **Route:** filter-chain rules (`/settings/**` specialist, `/submit/**` applicant…).
2. **Method:** `@PreAuthorize` on services (`ReviewService`, `SettingsService`, `SubmissionService`…), so every entry point — UI, API, future jobs — is covered.
3. **Data:** `LabelQueryService` scopes applicants to their company. Resources of another company return **404**, not 403, so their existence is not revealed.

## Consequences

- Defense in depth; integration tests cover each layer.
- Slight duplication of role rules between route and method layers (intentional).

## Alternatives considered

- Route-only checks: new controllers could forget them.
- Row-level security in PostgreSQL: strong, but ties authorization to one database and complicates H2 tests.
