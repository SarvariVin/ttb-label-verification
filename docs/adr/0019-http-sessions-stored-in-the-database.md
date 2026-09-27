# ADR-0019: HTTP sessions stored in the database

- **Status:** Accepted
- **Date:** 2026-09-24

## Context

Web sessions were held in the servlet container's memory. On Railway, the app sleeps when idle and restarts on every deploy, so each wake or deploy signed everyone out, often in the middle of a review. The same limitation blocks running more than one instance.

## Decision

- Use **Spring Session JDBC**, storing sessions in the application's own PostgreSQL database (H2 for demo and tests).
- Create the tables with a Flyway migration (`V3__http_sessions.sql`: `spring_session`, `spring_session_attributes`) and set `spring.session.jdbc.initialize-schema: never`, so the schema has a single owner ([ADR-0005](0005-portable-sql-schema-for-postgresql-and-h2.md)).
- Keep the existing cookie policy: 8-hour timeout, `HttpOnly`, `SameSite=Lax`, and `Secure` under the `railway` profile. The cookie is now named `SESSION`.
- Remove expired sessions every 5 minutes (`cleanup-cron`).
- The API chain stays stateless ([ADR-0006](0006-separate-security-filter-chains-for-api-and-ui.md)) and creates no sessions.

## Consequences

- Sign-ins survive restarts, deploys and sleep/wake. This was verified by restarting a local instance and reusing the same cookie (`JdbcSessionIntegrationTest`).
- Any number of app instances can share sessions with no sticky routing.
- Each authenticated request makes a small session read and write in PostgreSQL. This is negligible at demo and agency-pilot scale.
- Session data counts toward the database volume. It is small and cleaned up automatically.

## Alternatives considered

- **Redis:** faster, but it is another service to run and pay for. It remains the recommendation at high traffic ([production.md](../production.md#1-identity-and-access)).
- **Keep in-memory sessions:** users are signed out on every restart, and scaling out is impossible.
- **Stateless JWT for the UI:** no server-side revocation, and it would need CSRF handling redesigned.
