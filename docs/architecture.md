# Architecture

This document describes how TTB Label Verification is built, from the outside in: first the people and systems around it, then the runtime containers, then the layers inside the application, and finally how a label moves through them. Hosting and networking are covered in [infrastructure.md](infrastructure.md). The reasons behind each choice are recorded as [ADRs](adr/README.md).

Diagrams are Mermaid, and their sources live in [diagrams/](diagrams/).

## Contents

1. [Drivers and principles](#1-drivers-and-principles)
2. [Context view](#2-context-view)
3. [Container view](#3-container-view)
4. [Layered component view](#4-layered-component-view)
5. [Runtime view: submitting a label](#5-runtime-view-submitting-a-label)
6. [Runtime view: deciding on a label](#6-runtime-view-deciding-on-a-label)
7. [Verification engine](#7-verification-engine)
8. [Label lifecycle](#8-label-lifecycle)
9. [Data view](#9-data-view)
10. [Security view](#10-security-view)
11. [Presentation view](#11-presentation-view)
12. [Quality attributes](#12-quality-attributes)

---

## 1. Drivers and principles

| Driver | How the architecture answers it |
|--------|---------------------------------|
| Specialists must stay in charge | The AI only *proposes* a status. A person approves, reviews field by field, or overrides with a written reason, and every one of those actions is logged. |
| Works with no cloud accounts | The default pipeline is Tesseract, running on the same host. Cloud AI is an optional adapter behind the same interface ([ADR-0004](adr/0004-local-first-ocr-with-optional-cloud-ai-behind-a-pipeline-interface.md)). |
| Regulatory rules change | TTB rules live in plain-Java packages (`regulatory`, `labels`, `ai.compare`) with no framework code, so they are cheap to test and edit ([ADR-0002](adr/0002-modular-monolith-with-pure-rule-packages.md)). |
| Small, cheap hosting | One deployable JAR, one database, and a profile tuned for a 512 MB container ([ADR-0018](adr/0018-small-container-profile-for-paas-hosting.md)). |
| Government-grade web security | Server-rendered pages, a strict CSP, CSRF on every form, and authorization checked at three layers ([ADR-0003](adr/0003-server-rendered-ui-with-progressive-enhancement.md), [ADR-0007](adr/0007-authorization-at-route-method-and-data-layers.md)). |

**Design rules that hold everywhere**

- Dependencies point inward, toward the rules. Rule packages never import Spring, JPA or HTTP types.
- No AI or network call runs inside a database transaction ([ADR-0009](adr/0009-database-transactions-never-wrap-ai-calls.md)).
- Audit rows are only ever added, never updated, and they are written before the label changes ([ADR-0013](adr/0013-append-only-audit-trail-with-server-derived-history.md)).
- Status that depends on time is worked out when it is read, not by a scheduler ([ADR-0011](adr/0011-lazy-status-recovery-instead-of-a-scheduler.md)).

## 2. Context view

```mermaid
flowchart LR
    applicant(["Applicant<br/>industry labeling team"])
    specialist(["TTB labeling specialist"])
    client(["API client<br/>scripts, integrations"])

    lvs["<b>TTB Label Verification</b><br/>reads labels, compares them with<br/>the COLA application, proposes a verdict"]

    tess["Tesseract OCR<br/><i>on-host, default</i>"]
    vision["Google Cloud Vision<br/><i>opt-in</i>"]
    openai["OpenAI<br/><i>opt-in</i>"]

    applicant -- "submits labels + Form 5100.31 data,<br/>tracks own results" --> lvs
    specialist -- "works queues, decides,<br/>tunes settings" --> lvs
    client -- "REST /api/v1 (HTTP Basic)" --> lvs
    lvs -- "local OCR" --> tess
    lvs -. "word-level OCR" .-> vision
    lvs -. "field classification" .-> openai
```

<sub>Source: [diagrams/01-system-context.mmd](diagrams/01-system-context.mmd)</sub>

| Who or what | Relationship |
|-------------|--------------|
| Applicant | Uploads labels through the form, CSV batch or API, and sees only their own company's submissions and field outcomes. |
| Specialist | Owns the decision: works the queues, resolves fields, sets the final status, and edits runtime settings. |
| API client | Uses the same use cases over stateless REST. |
| Tesseract | The default OCR engine, a native library on the same host. |
| Google Vision + OpenAI | The optional cloud pipeline. It is switched on only when both keys are present, and adds bounding boxes and image-type classification. |

## 3. Container view

```mermaid
flowchart TB
    browser(["Browser<br/>server-rendered pages + app.js"])
    api(["API client"])

    subgraph host["Application container (one JVM, Java 21)"]
        web["Web layer<br/>Thymeleaf pages · REST /api/v1"]
        core["Application + rules<br/>services · verdict engine"]
        adapters["Adapters<br/>OCR · cloud AI · storage · JPA"]
        tessn["libtesseract<br/>(native, via Tess4J)"]
        web --> core --> adapters
        adapters --> tessn
    end

    db[("PostgreSQL 17<br/>labels · results · audit<br/>sessions · settings<br/>(images when storage=database)")]
    disk[("Filesystem volume<br/>images when storage=filesystem")]
    cloud["Google Vision + OpenAI<br/>(only when both keys are set)"]

    browser -- "HTTPS, session cookie + CSRF" --> web
    api -- "HTTPS, Basic auth, stateless" --> web
    adapters -- "JDBC, Flyway-owned schema" --> db
    adapters -- "file I/O" --> disk
    adapters -. "HTTPS" .-> cloud
```

<sub>Source: [diagrams/17-containers.mmd](diagrams/17-containers.mmd)</sub>

- **One application container.** Web, services, rules and adapters all run in a single Spring Boot 3.5 JVM. Scaling out means adding replicas behind a load balancer. Nothing lives only in one process's memory: sessions are in the database ([ADR-0019](adr/0019-http-sessions-stored-in-the-database.md)).
- **PostgreSQL is the system of record.** Flyway owns the schema, and the same scripts run on H2 for the demo profile and the tests ([ADR-0005](adr/0005-portable-sql-schema-for-postgresql-and-h2.md)).
- **Image storage can be swapped.** `app.storage.type=filesystem` writes to a volume. `database` stores the bytes in PostgreSQL, which suits platforms that allow only one volume, such as Railway.
- **Railway's database URL is converted automatically.** Railway hands out a `postgresql://user:pass@host/db` URL. `DatabaseUrlEnvironmentPostProcessor` turns it into JDBC settings before Spring starts, and fails early with a clear message if it is missing.

## 4. Layered component view

```mermaid
flowchart TB
    subgraph L1["Presentation — web.page · web.api · templates · static"]
        pages["Page controllers<br/>Dashboard · Submit · LabelPage<br/>Settings · Applicants · Login"]
        rest["REST controllers + DTOs<br/>LabelApi · SettingsApi<br/>ApiExceptionHandler (RFC 9457)"]
        view["Thymeleaf templates · layout fragments<br/>app.css (tokens) · app.js<br/>GlobalModelAdvice · ViewFormat"]
    end

    subgraph L2["Application — service (transactions + @PreAuthorize)"]
        intake["SubmissionService · BatchSubmissionService<br/>PrefillService"]
        decide["ReviewService<br/>review · override · batch approve · re-analyze"]
        read["LabelQueryService · SlaMetricsService<br/>ApplicantService · SettingsService"]
        analyze["LabelAnalysisService → ExtractionService<br/>pipeline choice · fallback · 60 s timeout"]
    end

    subgraph L3["Domain rules — plain Java, no Spring"]
        regs["regulatory<br/>BeverageType · FieldName · HealthWarning<br/>QualifyingPhrases · RegulatoryConstants"]
        verdict["labels<br/>StatusDeterminer · EffectiveStatus<br/>Deadlines · ExpectedFields · SlaStatus"]
        compare["ai.compare · ai.prefill<br/>FieldComparator · OcrTextSearch<br/>TextNormalizer · LabelFieldExtractor"]
    end

    subgraph L4["Adapters — outbound"]
        ocr["ai.local · ai.cloud · ai.ocr<br/>Tesseract · Google Vision · OpenAI"]
        persist["domain + repository<br/>JPA entities · Spring Data"]
        files["storage<br/>Local / Database ImageStorage<br/>ImageFileValidator"]
    end

    subgraph X["Platform — config · security"]
        plat["SecurityConfig (two filter chains, CSP)<br/>AppProperties · DatabaseUrlEnvironmentPostProcessor<br/>DataSeeder · UserProvisioner · DemoLoginService · ClockConfig"]
    end

    L1 --> L2
    L2 --> L3
    L2 --> L4
    L4 -. "implements contracts used by" .-> L3
    X -. "guards / configures" .-> L1
    X -. "guards / configures" .-> L2
```

<sub>Source: [diagrams/02-components.mmd](diagrams/02-components.mmd)</sub>

| Layer | Packages (`gov.ttb.labelverification.*`) | Owns | Must not |
|-------|-------------------------------------------|------|----------|
| Presentation | `web.page`, `web.api`, `templates/`, `static/` | HTTP mapping, form binding, DTOs, view formatting, page chrome | Hold business rules, or hand entities to the API |
| Application | `service` | Use cases, transaction boundaries, `@PreAuthorize`, orchestration | Call AI inside a transaction |
| Domain rules | `regulatory`, `labels`, `ai.compare`, `ai.prefill` | Mandatory fields, comparison strategies, verdicts, deadlines, SLA status | Import Spring, JPA or I/O |
| Adapters | `ai.local`, `ai.cloud`, `ai.ocr`, `domain`, `repository`, `storage` | OCR engines, cloud clients, persistence, image bytes | Decide a verdict |
| Platform | `config`, `security` | Filter chains, typed properties, bootstrap accounts, principal | Contain use-case logic |

The per-package detail is in the [package guide](../src/main/java/gov/ttb/labelverification/README.md).

## 5. Runtime view: submitting a label

```mermaid
sequenceDiagram
    autonumber
    actor A as Applicant
    box Web
        participant C as SubmitController /<br/>LabelApiController
    end
    box Application
        participant S as SubmissionService
        participant AN as LabelAnalysisService
        participant X as ExtractionService
    end
    box Adapters
        participant ST as ImageFileValidator +<br/>ImageStorage
        participant P as Pipeline<br/>(local or cloud)
        participant DB as Database
    end

    A->>C: multipart POST — form fields + 1–6 images
    C->>C: Bean Validation of LabelApplicationForm

    rect rgba(47, 111, 179, 0.08)
    Note over C,DB: Phase 1 — intake (TX1)
    C->>S: submit(user, form, images)
    loop for every image
        S->>ST: check magic bytes & size, then store bytes
    end
    S->>DB: TX1 — label PROCESSING + application_data + label_images
    end

    rect rgba(201, 162, 39, 0.10)
    Note over S,P: Phase 2 — analysis (no transaction open)
    S->>AN: analyze(labelId)
    AN->>DB: TX2 (read-only) — expected fields + image keys
    AN->>ST: load image bytes
    AN->>X: extract(images, beverage type, expected fields)
    X->>P: run, bounded by 60 s
    opt the chosen pipeline throws
        X->>P: try the other pipeline, if available
    end
    P-->>X: ExtractionResult — values, boxes, timings
    X-->>AN: result
    AN->>AN: FieldComparator per field, then StatusDeterminer
    end

    rect rgba(29, 122, 70, 0.08)
    Note over AN,DB: Phase 3 — results (TX3)
    AN->>DB: TX3 — validation_result + items,<br/>label PENDING_REVIEW with AI proposal + confidence
    end

    AN-->>S: outcome
    S-->>C: SubmissionResult
    C-->>A: 201 Created, or redirect to the label page
    Note over A,DB: Failure or timeout → label stays PENDING (Re-analyze retries).<br/>Stuck in PROCESSING for 5 min → shown as PENDING_REVIEW.
```

<sub>Source: [diagrams/03-submission-sequence.mmd](diagrams/03-submission-sequence.mmd)</sub>

A submission runs in three short transactions, with the slow analysis step outside all of them. TX1 records the label as `PROCESSING` with its data and images. Analysis then reads the images and calls the pipeline, with no transaction open. TX3 writes the field results and moves the label to `PENDING_REVIEW` with the AI's proposal.

| When this goes wrong | The system does this |
|----------------------|----------------------|
| An image has the wrong type, is too large, or has mismatched magic bytes | Returns 422 and stores nothing |
| Form data is invalid | Returns 400 from the API, or re-renders the form with messages |
| The chosen pipeline throws | Tries the other pipeline if it is available ([ADR-0010](adr/0010-pipeline-fallback-in-both-directions-not-on-timeout.md)) |
| Both pipelines fail, or 60 s pass | Keeps the label as `PENDING`, and a specialist can re-analyze |
| The process dies mid-analysis | After 5 minutes the label is shown as `PENDING_REVIEW` |

## 6. Runtime view: deciding on a label

```mermaid
flowchart TB
    dash(["Review dashboard<br/>effective status computed on read,<br/>saved back only if it changed"]) --> tabs{"Which queue?"}
    tabs -- "Ready to approve" --> ba
    tabs -- "Needs review / All" --> open["Open a label"]
    open --> how{"What does the<br/>specialist do?"}
    how -- "fix individual fields" --> fr
    how -- "decide the whole label" --> ov
    how -- "run the check again" --> ra

    subgraph ba["Batch approve — up to 100 labels"]
        direction TB
        ba1["For each label, in its own transaction"] --> ba2{"Still PENDING_REVIEW,<br/>confidence ≥ threshold,<br/>every item MATCH?"}
        ba2 -- yes --> ba3["status_override row (audit)<br/>label → APPROVED"]
        ba2 -- no --> ba4["returned in failedIds"]
    end

    subgraph fr["Field review"]
        direction TB
        fr1["Resolve fields as MATCH / MISMATCH / NOT_FOUND + note"] --> fr2["human_reviews row per changed field<br/>(original status read from the database)"]
        fr2 --> fr3["StatusDeterminer over resolved items<br/>→ new status + 7- or 30-day deadline"]
    end

    subgraph ov["Status override"]
        direction TB
        ov1["Decision + justification (≥ 10 characters)"] --> ov2["status_override row<br/>label → decision"]
    end

    subgraph ra["Re-analyze"]
        direction TB
        ra1["Run the pipeline with current settings"] --> ra2["Current validation_result superseded,<br/>new one inserted"]
    end
```

<sub>Source: [diagrams/04-review-sequence.mmd](diagrams/04-review-sequence.mmd)</sub>

- A label is **Ready to approve** when it is `PENDING_REVIEW`, the AI proposes `APPROVED`, every field is `MATCH`, and confidence is at or above the threshold. Batch approval checks all of this again on the server, one transaction per label, so a stale browser tab can't approve something that has since changed.
- **Field review** takes each field's original status from the database, never from the request, and then derives the label's status again from the resolved fields.

## 7. Verification engine

```mermaid
flowchart LR
    in(["Label images<br/>+ expected fields"]) --> pick{"settings.<br/>submission_pipeline_model"}

    subgraph read["1 · READ"]
        direction TB
        L1["<b>Local (default)</b><br/>Tesseract per image<br/>grayscale · upscale &lt;1024 px → 2048 px<br/>PSM 11 sparse + PSM 6 block, lines merged"]
        C1["<b>Cloud (opt-in)</b><br/>Google Vision TEXT_DETECTION<br/>images in parallel · word polygons"]
    end

    subgraph locate["2 · LOCATE"]
        direction TB
        L2["<b>OcrTextSearch</b> per expected field<br/>① exact, whole words/numbers<br/>② warning: landmark + all 6 phrases<br/>③ ignore spaces/punctuation<br/>④ sliding window (≥ .9 noise · .75–.9 label text)<br/>⑤ scattered words<br/>numbers keep the OCR text"]
        C2["<b>OpenAI classification</b><br/>indexed word list + beverage-type prompt<br/>strict JSON → field, value, wordIndices"]
        C3["<b>BoundingBoxMath</b><br/>union of word boxes → 0–1 coordinates"]
        C2 --> C3
    end

    subgraph judge["3 · JUDGE"]
        direction TB
        V{"Accepted<br/>variant?"} -- yes --> M["MATCH (95)"]
        V -- no --> FC["<b>FieldComparator</b><br/>EXACT · FUZZY (Dice ≥ .8)<br/>NORMALIZED (ABV · mL · years)<br/>CONTAINS · ENUM (qualifying phrases)"]
        FC --> MI{"Mismatch on a<br/>minor field?"}
        MI -- yes --> NC["NEEDS_CORRECTION"]
        MI -- no --> ST["MATCH / MISMATCH / NOT_FOUND"]
        M & NC & ST --> SD["<b>StatusDeterminer</b><br/>AI-proposed status<br/>overall = mean confidence"]
    end

    pick -- local --> L1
    pick -- cloud --> C1
    L1 --> L2
    C1 --> C2
    L2 & C3 --> R["ExtractionResult<br/>(same shape for both)"] --> V
    C1 -. "error → fall back" .-> L1
    L1 -. "error → fall back<br/>(if cloud configured)" .-> C1
```

<sub>Source: [diagrams/05-ai-pipeline.mmd](diagrams/05-ai-pipeline.mmd)</sub>

Both pipelines return the same `ExtractionResult`. From there the comparison and the verdict don't depend on which engine read the label. The search order, the thresholds and measured accuracy are in [ai-pipelines.md](ai-pipelines.md).

## 8. Label lifecycle

```mermaid
stateDiagram-v2
    direction LR
    [*] --> PROCESSING: applicant submits

    state "Automatic analysis" as auto {
        PROCESSING
        PENDING
    }
    state "Specialist decision" as wait {
        PENDING_REVIEW
    }
    state "Applicant has a deadline" as fix {
        CONDITIONALLY_APPROVED
        NEEDS_CORRECTION
    }
    state "Final" as done {
        APPROVED
        REJECTED
    }

    PROCESSING --> PENDING_REVIEW: analysis done, AI proposal stored
    PROCESSING --> PENDING: failed or timed out
    PROCESSING --> PENDING_REVIEW: stuck over 5 min (lazy)
    PENDING --> PROCESSING: specialist re-analyzes

    PENDING_REVIEW --> APPROVED: batch approve, review or override
    PENDING_REVIEW --> CONDITIONALLY_APPROVED: minor difference, 7 days
    PENDING_REVIEW --> NEEDS_CORRECTION: substantive issue, 30 days
    PENDING_REVIEW --> REJECTED: health warning or illegal size

    CONDITIONALLY_APPROVED --> NEEDS_CORRECTION: deadline passed (lazy)
    NEEDS_CORRECTION --> REJECTED: deadline passed (lazy)

    CONDITIONALLY_APPROVED --> APPROVED: override
    NEEDS_CORRECTION --> APPROVED: override
    REJECTED --> APPROVED: override

    note right of fix
        The applicant fixes a label by submitting
        a new one linked through prior_label_id
    end note
```

<sub>Source: [diagrams/06-label-status.mmd](diagrams/06-label-status.mmd)</sub>

The database stores a status, but every screen and API response shows the **effective** status. That is calculated when the label is read (expired correction windows, analyses stuck in processing) and saved back only when it has changed.

## 9. Data view

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
    LABEL_IMAGES ||--o| IMAGE_BLOBS : "bytes when storage=database (by storage_key)"
    SPRING_SESSION ||--o{ SPRING_SESSION_ATTRIBUTES : holds

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
    IMAGE_BLOBS {
        varchar storage_key PK "V2"
        varchar content_type
        int size_bytes
        bytea content
    }
    SPRING_SESSION {
        char primary_id PK "V3"
        char session_id
        bigint expiry_time
        varchar principal_name
    }
    SPRING_SESSION_ATTRIBUTES {
        char session_primary_id PK,FK
        varchar attribute_name PK
        bytea attribute_bytes
    }
```

<sub>Source: [diagrams/07-erd.mmd](diagrams/07-erd.mmd)</sub>

- **Identifiers** are random, URL-safe, 21-character strings (`domain/Ids.java`), so IDs reveal nothing about volume or order.
- **Analysis history is kept.** A re-analysis marks the current `validation_results` row as superseded and adds a new one.
- **The audit trail is append-only.** `human_reviews` records field decisions and `status_overrides` records label decisions.
- **Corrections form a chain** through `labels.prior_label_id`.
- **Runtime settings** are JSON values in `settings`, keyed by name. Deployment settings come from the environment through `AppProperties`.
- **Sessions** are stored in the Spring Session JDBC tables (Flyway `V3`).

## 10. Security view

```mermaid
flowchart LR
    req(["Request"]) --> split{"/api/** ?"}
    split -- yes --> apichain["API chain · @Order(1)<br/>stateless · HTTP Basic · no CSRF<br/>ignores session cookies · 401 when anonymous"]
    split -- no --> webchain["Web chain · @Order(2)<br/>form login · JDBC session · CSRF<br/>/submit/** → APPLICANT<br/>/settings/**, /applicants/** → SPECIALIST"]
    apichain --> headers["Response headers on both chains<br/>CSP self-only · frame-ancestors none<br/>HSTS · nosniff · strict referrer"]
    webchain --> headers
    headers --> method["Service methods<br/>@PreAuthorize by role"]
    method --> scope["Data scoping in queries<br/>applicants see only their company<br/>anything else → 404"]
```

<sub>Source: [diagrams/08-security.mmd](diagrams/08-security.mmd)</sub>

| Concern | Control |
|---------|---------|
| Who you are | Form login with a database-backed session for the UI, and HTTP Basic for the API ([ADR-0006](adr/0006-separate-security-filter-chains-for-api-and-ui.md)). Passwords are hashed with bcrypt. Demo mode (`APP_DEMO_LOGIN`) is off unless it is switched on explicitly. |
| What you can do | URL rules, then `@PreAuthorize` on services, then company scoping in queries ([ADR-0007](adr/0007-authorization-at-route-method-and-data-layers.md)) |
| What you can see | Another company's resources return 404, so their existence isn't revealed. Applicants never see AI confidence or reasoning. |
| Forged requests | CSRF tokens on every UI form. The API ignores cookies entirely. |
| Hostile input | Bean Validation, image type, size and magic-byte checks, and storage keys that can't escape their directory |
| Injected content | A CSP with no inline script or style, and no stack traces in any response |
| Secrets | None in code, defaults or docs ([ADR-0014](adr/0014-no-credentials-in-code-configuration-defaults-or-documentation.md)). Bootstrap passwords come from the environment or are generated and logged once. |

## 11. Presentation view

The UI is server-rendered HTML. A page is a Thymeleaf template assembled from shared fragments (head, top bar, flash messages, footer) and styled by one token-based stylesheet ([ADR-0021](adr/0021-design-tokens-and-shared-page-chrome.md)). JavaScript only adds conveniences, so every page works without it.

| Role | Screens |
|------|---------|
| Applicant | *My submissions* (summary tiles and the list), *New submission* (numbered steps, with pre-fill read from the label), *Batch upload*, *Label detail* (field outcomes and deadlines) |
| Specialist | *Review dashboard* (SLA tiles and the Ready / Needs review / All queues), *Label detail* (image with overlays, field review, final status, re-analyze, history), *Applicants*, *Settings* |
| Everyone | *Sign in* (brand panel and form, with the demo-account picker when enabled), *Error* |

Tokens, components, responsive breakpoints and the steps for adding a page are in the [UI guide](ui.md).

## 12. Quality attributes

| Attribute | Approach |
|-----------|----------|
| Correctness | Plain-Java rule packages with focused unit tests, plus end-to-end tests that run real OCR on the synthetic labels |
| Auditability | Every analysis run keeps the raw pipeline output, model, timings and token counts. Every human decision is its own row. |
| Resilience | Pipeline fallback, a hard timeout, lazy recovery of stuck labels, and sessions that survive restarts |
| Testability | An injected `Clock`, so tests run on fixed time. H2 in PostgreSQL mode runs the real Flyway scripts. |
| Concurrency | Virtual threads for the pipeline timeout and parallel cloud OCR. Each OCR pass gets its own Tesseract instance, and a limit caps concurrent OCR jobs. |
| Operability | Actuator health and info, structured logs for fallbacks and failures, and startup checks that explain missing configuration |
| Evolvability | Pipelines and storage are interfaces. The queue-based asynchronous design is sketched in [infrastructure.md](infrastructure.md#6-scaling-target) ([ADR-0008](adr/0008-synchronous-analysis-with-timeout-queue-based-evolution.md)). |
