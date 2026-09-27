# ADR-0014: No credentials in code, configuration defaults or documentation

- **Status:** Accepted
- **Date:** 2026-09-23

## Context

Hard-coded demo passwords and default database passwords tend to survive into real environments and leak through documentation.

## Decision

- No default database username/password; `docker compose` refuses to start without them in `.env`.
- Bootstrap accounts get their password from `APP_SEED_PASSWORD`; if unset, a random one is generated and logged once. Seeding is disabled in production (`APP_SEED=false`).
- Documentation and examples reference environment variables, never literal secrets.
- Test-only secrets exist only in `src/test/resources`.

## Consequences

- First run requires reading the log or setting one variable.
- Nothing sensitive to rotate after publishing docs.

## Alternatives considered

- Documented demo accounts: convenient, but a well-known credential.
