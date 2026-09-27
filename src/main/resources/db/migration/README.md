# Flyway migrations

- Name files `V<n>__<description>.sql`. Migrations only move forward: once a migration has shipped, never edit it. Add a new version instead.
- Write SQL that runs on both PostgreSQL and H2 (`MODE=PostgreSQL`), as set out in [ADR-0005](../../../../../docs/adr/0005-portable-sql-schema-for-postgresql-and-h2.md):
  - `VARCHAR` + `CHECK` instead of `CREATE TYPE … AS ENUM`
  - `TEXT` instead of `jsonb` (the application serializes JSON)
  - no reserved words as column names (`value`, `key`, `user`…)
- Hibernate runs with `ddl-auto: validate`, so the app refuses to start if the entities and the schema drift apart.

| Version | Contents |
|---------|----------|
| V1 | users, applicants, labels, label_images, application_data, validation_results, validation_items, human_reviews, status_overrides, settings, accepted_variants |
| V2 | image_blobs (database-backed image storage, used by the `railway` profile) |
| V3 | spring_session, spring_session_attributes (HTTP sessions stored in the database; Spring Session's own schema initialization is off) |
