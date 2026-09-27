# Architecture

How TTB Label Verification is structured internally: who uses it, how a label moves through the system, how data is stored, and how it is secured. Deployment and networking are in [infrastructure.md](infrastructure.md), and the reasoning behind each choice is in the [ADRs](adr/README.md).

All diagrams are Mermaid, with sources in [diagrams/](diagrams/).

## Contents

1. [System context](#1-system-context)
2. [Components](#2-components)
3. [Submission flow](#3-submission-flow)
4. [Review flow](#4-review-flow)
5. [AI pipeline](#5-ai-pipeline)
6. [Label status lifecycle](#6-label-status-lifecycle)
7. [Data model](#7-data-model)
8. [Security model](#8-security-model)
9. [Cross-cutting concerns](#9-cross-cutting-concerns)

---

## 1. System context

```mermaid
flowchart LR
    applicant(["👤 Applicant<br/>(industry labeling team)"])
    specialist(["👤 TTB Labeling Specialist"])

    subgraph lvs["TTB Label Verification — Spring Boot 3.5 / Java 21"]
        app["Web UI (Thymeleaf)<br/>REST API /api/v1<br/>AI verification pipeline"]
    end

    db[("PostgreSQL<br/>(H2 in demo profile)")]
    fs[("Image storage<br/>local filesystem")]
    tess["Tesseract OCR<br/>native libtesseract via Tess4J<br/><i>default · free · on-host</i>"]
    vision["Google Cloud Vision<br/>TEXT_DETECTION<br/><i>opt-in</i>"]
    openai["OpenAI Chat Completions<br/>structured output<br/><i>opt-in</i>"]

    applicant -- "submit labels + Form 5100.31 data" --> app
    specialist -- "review, approve, override, configure" --> app
    app -- "JPA / Flyway" --> db
    app -- "store / load images" --> fs
    app -- "OCR (local pipeline)" --> tess
    app -. "OCR with word boxes (cloud pipeline)" .-> vision
    app -. "classify OCR words into fields" .-> openai
```

<sub>Source: [diagrams/01-system-context.mmd](diagrams/01-system-context.mmd)</sub>


| Actor / system | Interaction |
|----------------|-------------|
| Applicant | Submits labels (form, API, CSV batch); sees only their company's labels |
| Specialist | Works the queues, reviews fields, sets final status, configures settings |
| PostgreSQL | System of record; schema owned by Flyway |
| Image storage | Uploaded label images, behind the `ImageStorage` interface |
| Tesseract | Default OCR engine; native library loaded through Tess4J (JNA) |
| Google Vision / OpenAI | Optional cloud pipeline; used only when both keys are configured |

## 2. Components

The application is a modular monolith ([ADR-0002](adr/0002-modular-monolith-with-pure-rule-packages.md)). `regulatory`, `labels` and `ai.compare` are plain Java with no framework imports.

```mermaid
flowchart TB
    subgraph web["web"]
        pages["page.*<br/>DashboardController · SubmitController<br/>LabelPageController · SettingsController<br/>ApplicantsController · LoginController"]
        api["api.*<br/>LabelApiController · SettingsApiController<br/>DTOs · ApiExceptionHandler (RFC 9457)"]
    end

    subgraph security["security + config"]
        sec["SecurityConfig<br/>API chain: stateless Basic<br/>Web chain: form login + CSRF + CSP"]
        seed["DataSeeder · AppProperties · ClockConfig"]
    end

    subgraph service["service"]
        sub["SubmissionService<br/>BatchSubmissionService"]
        rev["ReviewService<br/>(review · override · batch approve · re-analyze)"]
        qry["LabelQueryService · SlaMetricsService<br/>ApplicantService · SettingsService"]
        ana["LabelAnalysisService<br/>(shared verification pipeline)"]
        ext["ExtractionService<br/>(pipeline choice · fallback · timeout)"]
    end

    subgraph ai["ai"]
        local["local.LocalExtractionPipeline<br/>ocr.TesseractOcrEngine"]
        cloud["cloud.CloudExtractionPipeline<br/>ocr.GoogleVisionOcrEngine<br/>OpenAiFieldClassifier · BoundingBoxMath"]
        cmp["compare.FieldComparator<br/>compare.OcrTextSearch · TextNormalizer"]
    end

    subgraph rules["labels + regulatory (pure, no Spring)"]
        lbl["StatusDeterminer · EffectiveStatus<br/>Deadlines · ExpectedFields · SlaStatus"]
        reg["BeverageType · FieldName · HealthWarning<br/>QualifyingPhrases · RegulatoryConstants"]
    end

    subgraph data["domain + repository + storage"]
        repo["JPA entities & Spring Data repositories"]
        store["ImageStorage (LocalImageStorage)<br/>ImageFileValidator (magic bytes)"]
    end

    pages --> service
    api --> service
    sec -. guards .-> web
    sec -. "@PreAuthorize" .-> service
    sub --> ana
    rev --> ana
    ana --> ext
    ext --> local
    ext --> cloud
    local --> cmp
    ana --> cmp
    ana --> lbl
    rev --> lbl
    qry --> lbl
    cmp --> reg
    lbl --> reg
    service --> repo
    service --> store
```

<sub>Source: [diagrams/02-components.mmd](diagrams/02-components.mmd)</sub>


| Package (`gov.ttb.labelverification.*`) | Responsibility |
|-----------------------------------------|----------------|
| `regulatory` | TTB rules as data: mandatory fields per beverage type, standards of fill, health-warning text, qualifying phrases |
| `labels` | Verdict derivation, lazy status recovery, deadlines, SLA status |
| `ai.compare` | Field comparison engine and OCR text search |
| `ai.prefill` | Rule-based extraction of form values from OCR lines (pre-fill) |
| `ai.local`, `ai.cloud`, `ai.ocr` | Extraction pipelines and OCR engines |
| `service` | Use cases, transaction boundaries, method security |
| `web.page`, `web.api` | Thymeleaf controllers; REST controllers and DTOs |
| `domain`, `repository`, `storage` | JPA entities, Spring Data repositories, image storage and validation |
| `config`, `security` | Filter chains, typed properties, bootstrap seeding, principal |

## 3. Submission flow

```mermaid
sequenceDiagram
    autonumber
    actor A as Applicant
    participant C as SubmitController / LabelApiController
    participant S as SubmissionService
    participant V as ImageFileValidator
    participant FS as ImageStorage
    participant DB as Database
    participant AN as LabelAnalysisService
    participant X as ExtractionService
    participant P as Pipeline (local or cloud)

    A->>C: POST form data + images (multipart)
    C->>C: Bean Validation (LabelApplicationForm)
    C->>S: submit(user, form, images)
    loop each image
        S->>V: validate(bytes, declared type)
        V-->>S: detected type (magic bytes)
        S->>FS: store(bytes)
    end
    S->>DB: TX1 insert label (PROCESSING) + application_data + label_images
    S->>AN: analyze(labelId)
    AN->>DB: TX2 load expected fields + image keys
    AN->>FS: load image bytes
    AN->>X: extract(images, type, expected)
    X->>P: run with timeout (60s)
    alt primary pipeline fails
        X->>P: fallback pipeline (if available)
    end
    P-->>X: ExtractionResult (fields, boxes, metrics)
    X-->>AN: result
    AN->>AN: FieldComparator per field → StatusDeterminer
    AN->>DB: TX3 insert validation_result + items,<br/>label → PENDING_REVIEW, ai_proposed_status, confidence
    AN-->>S: Outcome
    S-->>C: SubmissionResult
    C-->>A: 201 Created / redirect to label page
    Note over S,DB: On failure the label is set to PENDING (retry via Re-analyze). A label stuck in PROCESSING surfaces as PENDING_REVIEW after 5 minutes.
```

<sub>Source: [diagrams/03-submission-sequence.mmd](diagrams/03-submission-sequence.mmd)</sub>


The AI call never runs inside a database transaction ([ADR-0009](adr/0009-database-transactions-never-wrap-ai-calls.md)).

| Failure | Result |
|---------|--------|
| Invalid image (type, size, magic bytes) | 422; nothing persisted |
| Validation error | 400 (API) / form re-rendered with messages (UI) |
| Selected pipeline fails | Other pipeline tried if available ([ADR-0010](adr/0010-pipeline-fallback-in-both-directions-not-on-timeout.md)) |
| Both fail, or timeout (60 s) | Label saved `PENDING`; specialist can re-analyze |
| Process crash mid-analysis | Label shown as `PENDING_REVIEW` after 5 minutes |

## 4. Review flow

```mermaid
sequenceDiagram
    autonumber
    actor SP as Specialist
    participant UI as Dashboard / Label page
    participant R as ReviewService
    participant DB as Database

    SP->>UI: Open dashboard
    UI->>DB: queues (effective status computed & persisted lazily)
    UI-->>SP: Ready to approve | Needs review | All

    alt Batch approve (Ready to approve tab)
        SP->>R: batchApprove(ids ≤ 100)
        loop each label — own transaction
            R->>DB: re-check PENDING_REVIEW, confidence ≥ threshold, all items MATCH
            R->>DB: insert status_override (audit) + label → APPROVED
        end
        R-->>SP: approvedCount, failedIds
    else Field-level review
        SP->>R: submitReview(labelId, [itemId → MATCH|MISMATCH|NOT_FOUND, note])
        R->>DB: insert human_review per changed field (original status read from DB)
        R->>R: StatusDeterminer over resolved items
        R->>DB: label → derived status + correction deadline (7 / 30 days)
    else Label-level override
        SP->>R: overrideStatus(labelId, decision, justification ≥ 10 chars)
        R->>DB: insert status_override + label → decision
    else Re-analyze
        SP->>R: reanalyze(labelId)
        R->>DB: supersede current validation_result, insert new one
    end
```

<sub>Source: [diagrams/04-review-sequence.mmd](diagrams/04-review-sequence.mmd)</sub>


- **Ready to approve** means `PENDING_REVIEW`, an AI proposal of `APPROVED`, confidence at or above the threshold, and every field `MATCH`. Batch approval re-checks all of this on the server, one transaction per label.
- **Field review** records the original status from the database, not from the request ([ADR-0013](adr/0013-append-only-audit-trail-with-server-derived-history.md)).

## 5. AI pipeline

```mermaid
flowchart TB
    start(["Label images + expected fields"]) --> setting{"settings.submission_pipeline_model"}
    setting -- "local (default)" --> L1
    setting -- "cloud" --> C1

    subgraph local["Local pipeline — free, on-host"]
        L1["Tesseract OCR per image<br/>grayscale · upscale &lt;1024px to 2048px<br/>PSM 11 (sparse) + PSM 6 (block), merged lines"]
        L2["OcrTextSearch per expected field<br/>1 exact, whole words/numbers →<br/>2 GOVERNMENT WARNING landmark + all 6 body phrases →<br/>3 space/punctuation-insensitive →<br/>4 sliding window (≥ .9 noise; .75–.9 label text) → 5 scattered words<br/>numeric fields keep the OCR text"]
        L1 --> L2
    end

    subgraph cloud["Cloud pipeline — opt-in"]
        C1["Stage 1: Google Vision TEXT_DETECTION<br/>parallel, word-level polygons"]
        C2["Stage 2: OpenAI classification<br/>indexed word list + beverage-type prompt<br/>strict JSON schema → field, value, wordIndices"]
        C3["Stage 3: BoundingBoxMath<br/>union of word boxes → normalized 0–1"]
        C1 --> C2 --> C3
    end

    L2 --> R["ExtractionResult"]
    C3 --> R
    C1 -. "error" .-> L1
    L1 -. "error, if cloud configured" .-> C1

    R --> V{"Accepted variant?"}
    V -- yes --> M["MATCH 95"]
    V -- no --> FC["FieldComparator by strategy<br/>EXACT · FUZZY (Dice ≥ .8) · NORMALIZED (ABV, mL, years)<br/>CONTAINS · ENUM (qualifying phrases)"]
    FC --> MI{"Mismatch on minor field?"}
    MI -- yes --> NC["NEEDS_CORRECTION"]
    MI -- no --> ST["MATCH / MISMATCH / NOT_FOUND"]
    M --> SD
    NC --> SD
    ST --> SD["StatusDeterminer → AI-proposed status<br/>mean confidence → overall confidence"]
```

<sub>Source: [diagrams/05-ai-pipeline.mmd](diagrams/05-ai-pipeline.mmd)</sub>


Details are in [ai-pipelines.md](ai-pipelines.md).

## 6. Label status lifecycle

```mermaid
stateDiagram-v2
    [*] --> PROCESSING: applicant submits
    PROCESSING --> PENDING_REVIEW: pipeline done (AI proposal stored)
    PROCESSING --> PENDING: pipeline failed / timed out
    PROCESSING --> PENDING_REVIEW: stuck > 5 min (lazy recovery)
    PENDING --> PROCESSING: specialist re-analyzes

    PENDING_REVIEW --> APPROVED: batch approve / review / override
    PENDING_REVIEW --> CONDITIONALLY_APPROVED: minor discrepancy (7-day window)
    PENDING_REVIEW --> NEEDS_CORRECTION: substantive issue (30-day window)
    PENDING_REVIEW --> REJECTED: health warning / illegal size

    CONDITIONALLY_APPROVED --> NEEDS_CORRECTION: deadline passed (lazy)
    NEEDS_CORRECTION --> REJECTED: deadline passed (lazy)

    CONDITIONALLY_APPROVED --> APPROVED: specialist override
    NEEDS_CORRECTION --> APPROVED: specialist override
    REJECTED --> APPROVED: specialist override

    note right of NEEDS_CORRECTION
        Applicant corrects by submitting a new label
        linked via prior_label_id
    end note
```

<sub>Source: [diagrams/06-label-status.mmd](diagrams/06-label-status.mmd)</sub>


Status is stored, but users see the **effective** status, which is computed on read and persisted when it differs ([ADR-0011](adr/0011-lazy-status-recovery-instead-of-a-scheduler.md)).

## 7. Data model

```mermaid
erDiagram
    APPLICANTS ||--o{ USERS : "applicant users"
    APPLICANTS ||--o{ LABELS : submits
    USERS ||--o{ LABELS : "decided by (specialist_id)"
    LABELS ||--o| LABELS : "prior_label_id (correction chain)"
    LABELS ||--|| APPLICATION_DATA : "Form 5100.31"
    LABELS ||--o{ LABEL_IMAGES : has
    LABELS ||--o{ VALIDATION_RESULTS : "analysis runs"
    VALIDATION_RESULTS ||--o| VALIDATION_RESULTS : superseded_by
    VALIDATION_RESULTS ||--o{ VALIDATION_ITEMS : "per field"
    LABEL_IMAGES ||--o{ VALIDATION_ITEMS : "found on"
    VALIDATION_ITEMS ||--o{ HUMAN_REVIEWS : "field overrides"
    USERS ||--o{ HUMAN_REVIEWS : writes
    LABELS ||--o{ STATUS_OVERRIDES : "status audit"
    USERS ||--o{ STATUS_OVERRIDES : writes

    USERS {
        varchar id PK
        varchar email UK
        varchar password_hash "bcrypt ({bcrypt} prefix)"
        varchar role "SPECIALIST | APPLICANT"
        varchar applicant_id FK
    }
    APPLICANTS {
        varchar id PK
        varchar company_name
        varchar contact_email
        text notes
    }
    LABELS {
        varchar id PK
        varchar applicant_id FK
        varchar specialist_id FK
        varchar prior_label_id FK
        varchar beverage_type
        int container_size_ml
        varchar status
        varchar ai_proposed_status
        numeric overall_confidence
        timestamptz correction_deadline
        boolean deadline_expired
    }
    APPLICATION_DATA {
        varchar id PK
        varchar label_id FK "unique"
        text brand_name
        text class_type
        text alcohol_content
        text net_contents
        text health_warning
        text name_and_address
        text qualifying_phrase
        text wine_spirits_fields "varietal, appellation, vintage, age, state…"
    }
    LABEL_IMAGES {
        varchar id PK
        varchar label_id FK
        varchar storage_key
        varchar content_type
        varchar image_type "FRONT|BACK|NECK|STRIP|OTHER"
        int sort_order
    }
    VALIDATION_RESULTS {
        varchar id PK
        varchar label_id FK
        varchar superseded_by FK
        boolean is_current
        text ai_raw_response "JSON"
        bigint processing_time_ms
        varchar model_used
    }
    VALIDATION_ITEMS {
        varchar id PK
        varchar validation_result_id FK
        varchar label_image_id FK
        varchar field_name
        text expected_value
        text extracted_value
        varchar status "MATCH|MISMATCH|NOT_FOUND|NEEDS_CORRECTION"
        numeric confidence
        numeric bbox_x_y_w_h "normalized 0-1"
    }
    HUMAN_REVIEWS {
        varchar id PK
        varchar validation_item_id FK
        varchar original_status
        varchar resolved_status
        text reviewer_notes
    }
    STATUS_OVERRIDES {
        varchar id PK
        varchar label_id FK
        varchar previous_status
        varchar new_status
        text justification
        varchar reason_code
    }
    SETTINGS {
        varchar id PK
        varchar setting_key UK
        text setting_value "JSON"
    }
    ACCEPTED_VARIANTS {
        varchar id PK
        varchar field_name
        text canonical_value
        text variant_value
    }
```

<sub>Source: [diagrams/07-erd.mmd](diagrams/07-erd.mmd)</sub>


- IDs are 21-character URL-safe random strings (`domain/Ids.java`).
- The schema is portable between PostgreSQL and H2 ([ADR-0005](adr/0005-portable-sql-schema-for-postgresql-and-h2.md)).
- `validation_results` keep a history: re-analysis supersedes the current result and never deletes it.
- `human_reviews` and `status_overrides` are append-only.
- `labels.prior_label_id` links a correction to the label it corrects.

## 8. Security model

```mermaid
flowchart LR
    req(["HTTP request"]) --> m{"path starts with /api/?"}
    m -- yes --> api["API chain (@Order 1)<br/>STATELESS · HTTP Basic · CSRF off<br/>session cookie ignored<br/>/api/v1/settings/** → SPECIALIST<br/>401 on missing credentials"]
    m -- no --> web["Web chain (@Order 2)<br/>form login · session cookie · CSRF tokens<br/>/settings/**, /applicants/** → SPECIALIST<br/>/submit/** → APPLICANT"]
    api --> hdr["Headers on both: CSP (no inline script/style),<br/>X-Frame-Options DENY, HSTS, nosniff,<br/>Referrer-Policy strict-origin-when-cross-origin"]
    web --> hdr
    hdr --> ctl["Controllers"]
    ctl --> svc["Services with @PreAuthorize<br/>(ReviewService, SettingsService, ApplicantService → SPECIALIST;<br/>SubmissionService, BatchSubmissionService → APPLICANT)"]
    svc --> data["LabelQueryService data scoping:<br/>applicants see only their company's labels;<br/>others → 404 (existence not leaked)"]
```

<sub>Source: [diagrams/08-security.mmd](diagrams/08-security.mmd)</sub>


| Layer | Mechanism |
|-------|-----------|
| Authentication | Form login + session (UI); stateless HTTP Basic (API) ([ADR-0006](adr/0006-separate-security-filter-chains-for-api-and-ui.md)). Passwords are hashed with bcrypt. Sessions are stored in the database, so they survive restarts ([ADR-0019](adr/0019-http-sessions-stored-in-the-database.md)). Optional demo mode (`APP_DEMO_LOGIN`) adds a passwordless account picker to the login page. |
| Authorization | Route rules, `@PreAuthorize` on services, and data scoping, with 404 for other companies' resources ([ADR-0007](adr/0007-authorization-at-route-method-and-data-layers.md)) |
| CSRF | Tokens on every UI form; the API chain never reads cookies |
| Input | Bean Validation; image type, size and magic-byte checks; path-traversal-safe storage keys |
| Output | Strict CSP with no inline script or style; no stack traces in responses |
| Credentials | None in code, configuration defaults or docs ([ADR-0014](adr/0014-no-credentials-in-code-configuration-defaults-or-documentation.md)) |
| Confidentiality of scoring | Applicants see field statuses, but not AI confidence or reasoning |

## 9. Cross-cutting concerns

| Concern | Approach |
|---------|----------|
| Time | Injected `Clock`; tests run on fixed time |
| Concurrency | Virtual threads for pipeline timeout and parallel cloud OCR; a new Tesseract instance per pass |
| Configuration | `AppProperties` (`app.*`) for deployment settings; the `settings` table for runtime settings |
| Errors | RFC 9457 `ProblemDetail` for the API; friendly error page for the UI |
| Observability | Actuator health and info; structured logs for pipeline fallbacks and failures |
| Auditability | Raw pipeline output, model, timings and tokens stored for every analysis run |
