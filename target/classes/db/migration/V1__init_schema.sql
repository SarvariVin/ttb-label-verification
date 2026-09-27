-- TTB Label Verification — initial schema.
-- Portable SQL: runs unchanged on PostgreSQL and on H2 (MODE=PostgreSQL).
--   * Enums are VARCHAR + CHECK constraints (JPA EnumType.STRING).
--   * JSON payloads are TEXT (serialized with Jackson in the application).
--   * Primary keys are 21-char URL-safe random IDs (same alphabet as domain/Ids.java).

CREATE TABLE applicants (
    id             VARCHAR(21)  PRIMARY KEY,
    company_name   VARCHAR(255) NOT NULL,
    contact_email  VARCHAR(255),
    contact_name   VARCHAR(255),
    notes          TEXT,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE users (
    id             VARCHAR(21)  PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    role           VARCHAR(20)  NOT NULL CHECK (role IN ('SPECIALIST', 'APPLICANT')),
    applicant_id   VARCHAR(21)  REFERENCES applicants (id),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE labels (
    id                   VARCHAR(21) PRIMARY KEY,
    specialist_id        VARCHAR(21) REFERENCES users (id),
    applicant_id         VARCHAR(21) REFERENCES applicants (id),
    prior_label_id       VARCHAR(21) REFERENCES labels (id),
    beverage_type        VARCHAR(30) NOT NULL
        CHECK (beverage_type IN ('DISTILLED_SPIRITS', 'WINE', 'MALT_BEVERAGE')),
    container_size_ml    INTEGER     NOT NULL,
    status               VARCHAR(30) NOT NULL,
    ai_proposed_status   VARCHAR(30),
    overall_confidence   NUMERIC(5, 2),
    correction_deadline  TIMESTAMP WITH TIME ZONE,
    deadline_expired     BOOLEAN     NOT NULL DEFAULT FALSE,
    is_priority          BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_labels_status CHECK (status IN (
        'PENDING', 'PROCESSING', 'PENDING_REVIEW', 'APPROVED',
        'CONDITIONALLY_APPROVED', 'NEEDS_CORRECTION', 'REJECTED')),
    CONSTRAINT chk_labels_ai_status CHECK (ai_proposed_status IS NULL OR ai_proposed_status IN (
        'PENDING', 'PROCESSING', 'PENDING_REVIEW', 'APPROVED',
        'CONDITIONALLY_APPROVED', 'NEEDS_CORRECTION', 'REJECTED'))
);

CREATE INDEX idx_labels_status ON labels (status);
CREATE INDEX idx_labels_applicant ON labels (applicant_id);
CREATE INDEX idx_labels_created ON labels (created_at);

CREATE TABLE label_images (
    id              VARCHAR(21)  PRIMARY KEY,
    label_id        VARCHAR(21)  NOT NULL REFERENCES labels (id),
    storage_key     VARCHAR(512) NOT NULL,
    image_filename  VARCHAR(255) NOT NULL,
    content_type    VARCHAR(50)  NOT NULL,
    image_type      VARCHAR(10)  NOT NULL
        CHECK (image_type IN ('FRONT', 'BACK', 'NECK', 'STRIP', 'OTHER')),
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_label_images_label ON label_images (label_id);

-- Mirrors TTB Form 5100.31 — one row per label.
CREATE TABLE application_data (
    id                     VARCHAR(21) PRIMARY KEY,
    label_id               VARCHAR(21) NOT NULL UNIQUE REFERENCES labels (id),
    serial_number          VARCHAR(100),
    brand_name             TEXT,
    fanciful_name          TEXT,
    class_type             TEXT,
    class_type_code        VARCHAR(10),
    alcohol_content        TEXT,
    net_contents           TEXT,
    health_warning         TEXT,
    name_and_address       TEXT,
    qualifying_phrase      TEXT,
    country_of_origin      TEXT,
    grape_varietal         TEXT,
    appellation_of_origin  TEXT,
    vintage_year           VARCHAR(10),
    sulfite_declaration    BOOLEAN,
    age_statement          TEXT,
    state_of_distillation  TEXT,
    fdc_yellow_5           BOOLEAN,
    cochineal_carmine      BOOLEAN,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE validation_results (
    id                  VARCHAR(21)  PRIMARY KEY,
    label_id            VARCHAR(21)  NOT NULL REFERENCES labels (id),
    superseded_by       VARCHAR(21)  REFERENCES validation_results (id),
    is_current          BOOLEAN      NOT NULL DEFAULT TRUE,
    ai_raw_response     TEXT         NOT NULL,
    processing_time_ms  BIGINT       NOT NULL,
    model_used          VARCHAR(100) NOT NULL,
    input_tokens        INTEGER,
    output_tokens       INTEGER,
    total_tokens        INTEGER,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_validation_results_label ON validation_results (label_id, is_current);

CREATE TABLE validation_items (
    id                    VARCHAR(21) PRIMARY KEY,
    validation_result_id  VARCHAR(21) NOT NULL REFERENCES validation_results (id),
    label_image_id        VARCHAR(21) REFERENCES label_images (id),
    field_name            VARCHAR(40) NOT NULL,
    expected_value        TEXT        NOT NULL,
    extracted_value       TEXT        NOT NULL,
    status                VARCHAR(20) NOT NULL
        CHECK (status IN ('MATCH', 'MISMATCH', 'NOT_FOUND', 'NEEDS_CORRECTION')),
    confidence            NUMERIC(5, 2) NOT NULL,
    match_reasoning       TEXT,
    bbox_x                NUMERIC(8, 6),
    bbox_y                NUMERIC(8, 6),
    bbox_width            NUMERIC(8, 6),
    bbox_height           NUMERIC(8, 6),
    bbox_angle            NUMERIC(6, 2),
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_validation_items_result ON validation_items (validation_result_id);

CREATE TABLE human_reviews (
    id                  VARCHAR(21) PRIMARY KEY,
    specialist_id       VARCHAR(21) NOT NULL REFERENCES users (id),
    label_id            VARCHAR(21) NOT NULL REFERENCES labels (id),
    validation_item_id  VARCHAR(21) REFERENCES validation_items (id),
    original_status     VARCHAR(20) NOT NULL,
    resolved_status     VARCHAR(20) NOT NULL
        CHECK (resolved_status IN ('MATCH', 'MISMATCH', 'NOT_FOUND')),
    reviewer_notes      TEXT,
    annotation_data     TEXT,
    reviewed_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE status_overrides (
    id               VARCHAR(21) PRIMARY KEY,
    label_id         VARCHAR(21) NOT NULL REFERENCES labels (id),
    specialist_id    VARCHAR(21) NOT NULL REFERENCES users (id),
    previous_status  VARCHAR(30) NOT NULL,
    new_status       VARCHAR(30) NOT NULL,
    justification    TEXT        NOT NULL,
    reason_code      VARCHAR(50),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE settings (
    id          VARCHAR(21)  PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    setting_value TEXT       NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE accepted_variants (
    id               VARCHAR(21) PRIMARY KEY,
    field_name       VARCHAR(40) NOT NULL,
    canonical_value  TEXT        NOT NULL,
    variant_value    TEXT        NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL
);
