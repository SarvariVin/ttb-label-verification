# Flyway migrations

- Naming: `V<n>__<description>.sql`. Migrations are forward-only: never edit a shipped migration; add a new version.
- Keep SQL portable between PostgreSQL and H2 (`MODE=PostgreSQL`), per [ADR-0005](../../../../../docs/adr/0005-portable-sql-schema-for-postgresql-and-h2.md):
  - `VARCHAR` + `CHECK` instead of `CREATE TYPE … AS ENUM`
  - `TEXT` instead of `jsonb` (the application serializes JSON)
  - no reserved words as column names (`value`, `key`, `user`…)
- Hibernate runs with `ddl-auto: validate`, so startup fails if entities and schema drift apart.

| Version | Contents |
|---------|----------|
| V1 | users, applicants, labels, label_images, application_data, validation_results, validation_items, human_reviews, status_overrides, settings, accepted_variants |
| V2 | image_blobs (database-backed image storage, used by the `railway` profile) |
| V3 | spring_session, spring_session_attributes (HTTP sessions stored in the database; Spring Session's own schema initialization is off) |
