# ADR-0006: Separate security filter chains for API and UI

- **Status:** Accepted
- **Date:** 2026-09-23

## Context

The UI uses session cookies and needs CSRF protection. API clients cannot easily handle CSRF tokens. Disabling CSRF for an API that also accepts the session cookie would let any website forge API calls from a signed-in browser.

## Decision

Two `SecurityFilterChain` beans:
- `/api/**` — **stateless** HTTP Basic, CSRF disabled; the session is never read, so cookie-based forgery is impossible.
- everything else — form login, session cookie, CSRF tokens.

Both chains send CSP, `X-Frame-Options: DENY`, HSTS, `nosniff`, and a strict referrer policy.

## Consequences

- Safe CSRF posture for both clients.
- API clients send credentials on every request; production replaces Basic with OAuth2/JWT from the agency IdP ([production.md](../production.md#1-identity-and-access)).

## Alternatives considered

- One chain with CSRF ignored for `/api/**`: vulnerable (see Context).
- Token-only for both: worse UX for server-rendered pages.
