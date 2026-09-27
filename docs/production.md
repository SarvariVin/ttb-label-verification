# Production Readiness

What the current build deliberately leaves out, and what a production deployment for a federal agency needs. It is ordered roughly by risk. Target topology, network zones and CI/CD are in [infrastructure.md](infrastructure.md).

## 1. Identity and access

| Gap | Recommendation |
|-----|----------------|
| Local username/password accounts | Federate to the agency IdP (SAML/OIDC with PIV/CAC and MFA) using `spring-boot-starter-oauth2-client`. Set `APP_SEED=false`. |
| API uses HTTP Basic | OAuth2 resource server (JWT) with scopes per endpoint |
| No lockout or password reset | Delegated to the IdP; until then, add throttling on failed logins |
| Two roles | Add supervisor/manager (settings, reassignment, reporting) and per-specialist queues |
| Sessions in the application database (Spring Session JDBC) | Sessions already survive restarts and work across instances. At high traffic, move them to Redis to take load off PostgreSQL. |
| Demo-account picker (`APP_DEMO_LOGIN`) | Keep it off in every non-demo environment. Anyone can sign in while it is on. |

## 2. Security hardening

- Rate limiting on login and submission (gateway, or Bucket4j).
- Malware scanning of uploads (for example a ClamAV sidecar). Re-encode images server-side to strip metadata and polyglots.
- Secrets in a secrets manager with rotation; never in images or repositories.
- Workload identity instead of an API key for the OCR service.
- Dependency, SAST, container scanning and SBOM in CI ([infrastructure.md §5](infrastructure.md#5-cicd-pipeline)).
- Keep the CSP strict. Future inline code should use nonces, not `unsafe-inline`.

## 3. Throughput

Move analysis to queue-based workers with an outbox, idempotent processing and a dead-letter queue ([infrastructure.md §6](infrastructure.md#6-scaling-target)). Also:

- Replace the dashboard's per-label "all fields match" query with an aggregate query or a denormalized column. It is N+1 today.
- Paginate dashboard and API lists.

## 4. Storage and records

- Implement `ImageStorage` for object storage with server-side encryption, and serve images through the existing authorization-checked endpoint or short-lived signed URLs.
- Apply retention for images and audit records per the agency records schedule.

## 5. AI pipeline

- **Versioning:** store pipeline version, prompt hash and model version with every `validation_result`.
- **Accuracy benchmarking:** build a labeled set of real, consented submissions. Track per-field precision and recall per pipeline, and gate model or prompt changes on it. The synthetic labels are not a substitute.
- **Authorized providers:** the cloud pipeline sits behind `ExtractionPipeline`. Target FedRAMP-authorized OCR and LLM endpoints.
- **Image quality gate:** warn on uploads under about 1000 px wide.
- **Field strictness:** apply the stored strict/moderate/lenient settings to comparison thresholds.

## 6. Operations

- Micrometer metrics: pipeline latency by stage, fallback, timeout and error rates, queue depth, AI–specialist agreement.
- Structured JSON logs with correlation IDs; alerts on fallback spikes and timeouts.
- Managed PostgreSQL with point-in-time recovery; Flyway as a separate deploy step.
- Tested backup restore; RPO/RTO agreed with the program office.

## 7. Notifications

Notify applicants of decisions and approaching deadlines, using an outbox table so messages are sent only after the transaction commits.

## 8. Compliance and accessibility

- **Section 508 / WCAG 2.1 AA:** semantic HTML, labels and focus styles are in place. Add automated axe checks in CI and a manual screen-reader review.
- **ATO:** system security plan, continuous monitoring, and tamper-evident audit export (hash chaining or WORM storage).
- **Privacy:** privacy impact assessment for applicant contact data.

## 9. Roadmap

Features beyond the current scope:

- Interactive image viewer: pan/zoom, click a field to zoom to its box, draw annotations on review.
- Track which submitted values were accepted unchanged from pre-fill, so specialists can see where an applicant relied on OCR.
- Dashboard of AI errors (fields specialists most often overturn).
- Regulation quick-reference linked from each field.
- Keyboard shortcuts for high-throughput review.
- Communication letters generated from the decision and field findings.
