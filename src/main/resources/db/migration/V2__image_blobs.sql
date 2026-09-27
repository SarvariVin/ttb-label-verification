-- Optional image storage inside the database (app.storage.type=database).
-- BYTEA is native in PostgreSQL and an alias for VARBINARY in H2.
CREATE TABLE image_blobs (
    storage_key   VARCHAR(64)  PRIMARY KEY,
    content_type  VARCHAR(50)  NOT NULL,
    size_bytes    INTEGER      NOT NULL,
    content       BYTEA        NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
