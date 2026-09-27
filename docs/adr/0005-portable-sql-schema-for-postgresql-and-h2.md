# ADR-0005: Portable SQL schema for PostgreSQL and H2

- **Status:** Accepted
- **Date:** 2026-09-23

## Context

Production uses PostgreSQL. Developers and CI need a zero-dependency database for demos and integration tests, without Docker.

## Decision

Write one Flyway migration that runs unchanged on PostgreSQL and on H2 in PostgreSQL mode:
- enums as `VARCHAR` + `CHECK` constraints (JPA `EnumType.STRING`);
- JSON payloads as `TEXT` serialized by Jackson;
- no reserved words as column names (`setting_key`, `setting_value`);
- Hibernate `ddl-auto: validate` so schema drift fails at startup.

## Consequences

- `demo` profile and integration tests run anywhere with Java.
- No `jsonb` operators; nothing queries into JSON today.
- Adding an enum value requires a migration altering the `CHECK`.

## Alternatives considered

- Postgres-native types + Testcontainers: needs Docker on every machine and CI agent.
- Separate H2 schema: two schemas drift.
