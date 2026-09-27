# Infrastructure

Where TTB Label Verification runs, from a developer laptop to a production deployment, and how the network, secrets and delivery pipeline are arranged. For the application's internal design see [architecture.md](architecture.md).

## Contents

1. [Local development](#1-local-development)
2. [Container topology](#2-container-topology)
3. [Production reference topology](#3-production-reference-topology)
4. [Network zones](#4-network-zones)
5. [CI/CD pipeline](#5-cicd-pipeline)
6. [Scaling target](#6-scaling-target)
7. [Configuration and secrets](#7-configuration-and-secrets)
8. [Sizing guidance](#8-sizing-guidance)
9. [Platform-as-a-service hosting](#9-platform-as-a-service-hosting)

---

## 1. Local development

```mermaid
flowchart LR
    dev(["Developer"]) --> browser["Browser<br/>localhost:8080"]
    dev --> cli["curl / API client<br/>HTTP Basic"]
    subgraph ws["Workstation (macOS / Linux)"]
        jvm["JVM 21<br/>./mvnw spring-boot:run"]
        tess["Tesseract 5<br/>libtesseract + eng.traineddata"]
        h2[("H2 in-memory<br/>profile: demo")]
        fs[("./data/uploads")]
        subgraph compose["docker compose (optional)"]
            pg[("PostgreSQL 17<br/>127.0.0.1:5432")]
        end
    end
    browser --> jvm
    cli --> jvm
    jvm -- JNA --> tess
    jvm -- "profile: demo" --> h2
    jvm -- "default profile" --> pg
    jvm --> fs
    jvm -. "optional HTTPS" .-> ext["Google Vision / OpenAI"]
```

<sub>Source: [diagrams/09-infra-local.mmd](diagrams/09-infra-local.mmd)</sub>


| Mode | Command | Database | Notes |
|------|---------|----------|-------|
| Demo | `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo` | H2 in memory | Nothing to install except Java 21 and Tesseract; data is lost on restart |
| Standard | `docker compose up -d postgres` then `./mvnw spring-boot:run` | PostgreSQL 17 | Requires `DATABASE_USERNAME` / `DATABASE_PASSWORD` in `.env` |

Tesseract library and language-data paths are auto-detected for Homebrew (`/opt/homebrew`), `/usr/local`, and Debian/Ubuntu packages, or set with `TESSERACT_LIBRARY_PATH` / `TESSDATA_PREFIX`.

## 2. Container topology

```mermaid
flowchart TB
    user(["Browser / API client"]) -->|":8080"| app
    subgraph host["Container host"]
        subgraph net["compose network"]
            app["app container<br/>eclipse-temurin:21-jre + tesseract-ocr<br/>uid 10001 (non-root)<br/>healthcheck /actuator/health"]
            pg[("postgres:17-alpine<br/>bound to 127.0.0.1 only")]
        end
        v1[("volume: uploads<br/>/app/data/uploads")]
        v2[("volume: pgdata")]
        env[".env (not committed)<br/>DATABASE_* · APP_SEED_PASSWORD<br/>optional AI keys"]
    end
    app -->|JDBC 5432| pg
    app --- v1
    pg --- v2
    env -. injected at start .-> app
    env -. injected at start .-> pg
    app -. "HTTPS 443 (optional)" .-> ai["Google Vision / OpenAI"]
```

<sub>Source: [diagrams/10-infra-container.mmd](diagrams/10-infra-container.mmd)</sub>


The `Dockerfile` is a two-stage build: Maven builds the jar, then it's copied onto `eclipse-temurin:21-jre` with `tesseract-ocr` installed. Hardening:

- The app runs as a non-root user (uid 10001).
- `HEALTHCHECK` polls `/actuator/health`.
- JVM heap is capped at 75% of the container memory (`-XX:MaxRAMPercentage=75`).
- PostgreSQL is published on `127.0.0.1` only.
- Credentials come from `.env` and are never baked into the image. `docker compose` refuses to start without them.

```bash
docker compose --profile app up --build
```

## 3. Production reference topology

A cloud-neutral layout. It maps directly onto AWS (GovCloud), Azure Government, or an on-premises Kubernetes cluster.

```mermaid
flowchart TB
    users(["Specialists (agency network / VPN)"]) --> waf
    apps(["Applicants (internet)"]) --> waf
    idp["Agency identity provider<br/>SAML / OIDC (PIV/CAC, MFA)"]

    subgraph edge["Edge zone"]
        waf["WAF + DDoS protection"] --> lb["Load balancer<br/>TLS 1.2+ termination"]
    end

    subgraph appzone["Application zone (private subnets, 2+ availability zones)"]
        a1["App instance A"]
        a2["App instance B"]
        w1["Analysis workers<br/>(Tesseract, CPU-sized)"]
        q[("Work queue<br/>+ dead-letter queue")]
    end

    subgraph datazone["Data zone (no internet route)"]
        db[("Managed PostgreSQL<br/>multi-AZ · PITR · encrypted")]
        obj[("Object storage<br/>SSE · versioning · lifecycle")]
        sess[("Session store<br/>Redis / JDBC")]
    end

    subgraph shared["Shared services"]
        sm["Secrets manager"]
        obs["Logs · metrics · traces · alerts"]
        egress["Egress proxy / allow-list"]
    end

    lb --> a1 & a2
    a1 & a2 -. OIDC .-> idp
    a1 & a2 --> q --> w1
    a1 & a2 & w1 --> db
    a1 & a2 & w1 --> obj
    a1 & a2 --> sess
    a1 & a2 & w1 -. read secrets .-> sm
    a1 & a2 & w1 -. telemetry .-> obs
    w1 -. "optional cloud AI (authorized services only)" .-> egress --> ai["OCR / LLM endpoints"]
```

<sub>Source: [diagrams/11-infra-production.mmd](diagrams/11-infra-production.mmd)</sub>


| Component | Purpose | Examples |
|-----------|---------|----------|
| WAF + load balancer | TLS termination, OWASP rule set, rate limiting | AWS WAF + ALB, Azure Front Door + App Gateway, F5 |
| App instances (≥ 2, multi-AZ) | Web UI + REST API; stateless apart from the session store | ECS/EKS, AKS, OpenShift |
| Analysis workers | CPU-bound OCR, scaled on queue depth | Same image, worker profile |
| Work queue + DLQ | Decouples submission from analysis ([§6](#6-scaling-target)) | SQS, Azure Service Bus, RabbitMQ |
| Managed PostgreSQL | System of record; multi-AZ, point-in-time recovery, encryption at rest | RDS/Aurora, Azure Database for PostgreSQL |
| Object storage | Label images; server-side encryption, versioning, retention lifecycle | S3, Azure Blob — implement `ImageStorage` |
| Session store | Shared HTTP sessions across instances | Spring Session + Redis or JDBC |
| Identity provider | SAML/OIDC with PIV/CAC and MFA | Agency IdP, Login.gov |
| Secrets manager | Database and API credentials, rotated | AWS Secrets Manager, Azure Key Vault, Vault |
| Observability | Logs, metrics (Micrometer), traces, alerting | CloudWatch, Azure Monitor, Prometheus/Grafana, Splunk |
| Egress proxy | Allow-listed outbound access to authorized AI endpoints only | NAT + proxy, Azure Firewall |

## 4. Network zones

Deny by default; only the flows shown are allowed.

```mermaid
flowchart LR
    internet(["Internet"]) -->|"443 only"| edge
    subgraph edge["Edge zone"]
        waf["WAF / LB"]
    end
    subgraph app["Application zone"]
        svc["App + workers"]
    end
    subgraph data["Data zone"]
        db[("PostgreSQL :5432")]
        obj[("Object storage (private endpoint)")]
    end
    subgraph mgmt["Management zone"]
        bastion["Session manager / bastion<br/>(no SSH from internet)"]
        ci["CI/CD deploy role"]
    end
    edge -->|"8080 health-checked"| app
    app -->|"5432 TLS"| db
    app -->|"HTTPS private endpoint"| obj
    app -->|"443 via egress proxy<br/>allow-listed hosts only"| out(["External AI APIs"])
    mgmt -->|"admin, audited"| app
    mgmt -->|"migrations"| db
    internet -. "blocked" .-x data
    internet -. "blocked" .-x app
```

<sub>Source: [diagrams/12-network-zones.mmd](diagrams/12-network-zones.mmd)</sub>


| From → To | Port / protocol | Purpose |
|-----------|-----------------|---------|
| Internet → Edge | 443 TLS | Users and API clients |
| Edge → Application | 8080 HTTP (private) | Load-balanced traffic, health checks |
| Application → Data | 5432 TLS | PostgreSQL |
| Application → Object storage | HTTPS private endpoint | Label images |
| Application → Egress proxy → AI APIs | 443 TLS | Optional cloud pipeline; allow-listed hosts |
| Management → Application / Data | Audited sessions | Operations, migrations |

The data zone has no route to or from the internet.

## 5. CI/CD pipeline

```mermaid
flowchart LR
    commit["Commit / merge request"] --> build["Build & test<br/>scripts/ci.sh<br/>./mvnw verify (unit, integration, real-OCR end-to-end)"]
    build --> scan["Security scans<br/>dependency CVEs · SAST<br/>SBOM (CycloneDX)"]
    scan --> image["Container image<br/>docker build · image scan<br/>sign + push to registry"]
    image --> stg["Deploy: staging<br/>Flyway migrate · smoke tests<br/>/actuator/health"]
    stg --> approve{"Change approval"}
    approve -->|approved| prod["Deploy: production<br/>rolling / blue-green"]
    approve -->|rejected| stop(["Stop"])
    prod --> verify["Post-deploy checks<br/>health · error rate · pipeline latency"]
    verify -->|regression| rollback["Roll back image<br/>(schema is forward-only)"]
```

<sub>Source: [diagrams/13-cicd-pipeline.mmd](diagrams/13-cicd-pipeline.mmd)</sub>


`scripts/ci.sh` is the single, vendor-neutral build entry point. Any CI system (Jenkins, GitLab CI, Azure DevOps, TeamCity, Bamboo) runs it on an agent with Java 21, Tesseract and Docker:

```bash
./scripts/ci.sh
```

Steps to add around it in the CI system:

- Dependency CVE scanning (OWASP Dependency-Check / Grype) and SAST.
- SBOM generation (CycloneDX Maven plugin).
- Image scanning and signing.
- Flyway migration as its own deploy step, before new instances start.
- Smoke tests against `/actuator/health` and a synthetic label submission.

Migrations are forward-only, so rollback means redeploying the previous image against a backward-compatible schema.

## 6. Scaling target

Analysis currently runs inside the submit request ([ADR-0008](adr/0008-synchronous-analysis-with-timeout-queue-based-evolution.md)). At higher volume, move it to workers:

```mermaid
sequenceDiagram
    autonumber
    actor A as Applicant
    participant API as App instance
    participant DB as PostgreSQL
    participant OS as Object storage
    participant Q as Work queue
    participant W as Analysis worker
    A->>API: POST label + images
    API->>OS: store images
    API->>DB: insert label (PROCESSING) + outbox event
    API-->>A: 202 Accepted + labelId
    DB-->>Q: outbox relay publishes AnalyzeLabel(labelId)
    Q->>W: deliver (at-least-once)
    W->>OS: load images
    W->>W: OCR → compare → verdict (idempotent per labelId)
    W->>DB: persist result, label → PENDING_REVIEW
    alt repeated failure
        Q->>Q: move to dead-letter queue, alert
    end
    A->>API: GET label (poll / server-sent events)
    API-->>A: status + field results
```

<sub>Source: [diagrams/14-scaling-target.mmd](diagrams/14-scaling-target.mmd)</sub>


- A transactional **outbox** guarantees an analysis event for every stored label.
- Workers are **idempotent** per label ID (at-least-once delivery), and failures go to a dead-letter queue with alerting.
- The label page polls, or uses server-sent events, until the status leaves `PROCESSING`. The existing lazy recovery ([ADR-0011](adr/0011-lazy-status-recovery-instead-of-a-scheduler.md)) remains the safety net.

## 7. Configuration and secrets

| Setting | Source | Secret? |
|---------|--------|---------|
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Secrets manager → environment | Yes (password) |
| `APP_SEED`, `APP_SEED_PASSWORD` | Environment. Set `APP_SEED=false` in production | Yes (password) |
| `GOOGLE_VISION_API_KEY`, `OPENAI_API_KEY` | Secrets manager → environment | Yes |
| `OPENAI_MODEL`, `OPENAI_BASE_URL` | Environment | No |
| `TESSDATA_PREFIX`, `TESSERACT_LIBRARY_PATH` | Image / environment | No |
| `APP_STORAGE_DIR` | Environment (volume mount) | No |
| Pipeline choice, approval threshold, SLA targets | `settings` table, edited by specialists | No |

No credential has a default value in code or configuration files ([ADR-0014](adr/0014-no-credentials-in-code-configuration-defaults-or-documentation.md)).

## 8. Sizing guidance

Starting points. Analysis time was measured locally on the synthetic 1600×2000 px labels; the other values are estimates to confirm with load testing.

| Resource | Guidance |
|----------|----------|
| Local analysis (measured) | About 0.5–0.8 s per single-image label; scale workers by CPU cores |
| Memory (estimate) | 1 GB heap handles concurrent uploads of 10 MB images; add about 200 MB per concurrent OCR |
| Database (estimate) | Small: about 10 rows per label plus audit rows; images live outside the database |
| Storage (estimate) | About 150 KB–10 MB per image; apply a retention lifecycle |

## 9. Platform-as-a-service hosting

For small deployments, the app runs on a PaaS with two services: the app container and managed PostgreSQL. The `railway` profile and the Dockerfile's JVM defaults fit a 512 MB container, with a measured peak of 402 MB under 8 concurrent submissions. Images can be stored in PostgreSQL when only one volume is available ([ADR-0018](adr/0018-small-container-profile-for-paas-hosting.md)).

Step-by-step guide: [deploy-railway.md](deploy-railway.md). The same profile works on other platforms that terminate TLS and inject a `postgres://` database URL.
