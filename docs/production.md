# Production Readiness

This page lists what the current build leaves out on purpose, and what a federal production deployment would still need. Items are ordered roughly from highest risk to lowest. The target topology, network zones and delivery pipeline are in [infrastructure.md](infrastructure.md).

| # | Area | Status today | Must have before go-live |
|---|------|--------------|--------------------------|
| 1 | Identity and access | Local accounts, HTTP Basic API | IdP federation with PIV/CAC and MFA |
| 2 | Security hardening | Strict CSP, CSRF, magic-byte checks | Rate limits, malware scanning, managed secrets |
| 3 | Throughput | Analysis runs inside the request | Queue-based workers for real volume |
| 4 | Storage and records | Filesystem or database images | Encrypted object storage and retention |
| 5 | AI pipeline | Measured on synthetic labels | A benchmark built from real submissions |
| 6 | Operations | Health endpoint and logs | Metrics, alerts, tested restores |
| 7 | Notifications | None | Decision and deadline notices |
| 8 | Compliance | Accessible markup | ATO package, 508 audit, PIA |

## 1. Identity and access

| Today | What production needs |
|-------|-----------------------|
| Local email and password accounts | Federate with the agency IdP (SAML or OIDC, with PIV/CAC and MFA) through `spring-boot-starter-oauth2-client`, and set `APP_SEED=false` |
| HTTP Basic on the API | An OAuth2 resource server that takes JWTs, with scopes per endpoint |
| No lockout or password reset | Handled by the IdP. Until then, throttle failed sign-ins. |
| Two roles | Add a supervisor or manager role (settings, reassignment, reporting) and per-specialist queues |
| Sessions in the application database (Spring Session JDBC) | These already survive restarts and work across instances. Under heavy traffic, move them to Redis to take load off PostgreSQL. |
| Bootstrap test accounts (one specialist, two applicants) | Useful for demos only. Set `APP_SEED=false` and manage people through the IdP, or through `APP_USERS` with bcrypt hashes. |
| The demo-account picker (`APP_DEMO_LOGIN`) | Leave it off everywhere except demos. While it is on, anyone can sign in. |

## 2. Security hardening

- Rate-limit sign-in and submission, at the gateway or with Bucket4j.
- Scan uploads for malware (for example with a ClamAV sidecar), and re-encode images on the server to strip metadata and polyglot content.
- Keep secrets in a secrets manager with rotation, never in images or repositories.
- Give the workload its own identity for the OCR service, instead of an API key.
- Add dependency scanning, static analysis, container scanning and an SBOM to CI ([infrastructure.md §5](infrastructure.md#5-cicd-pipeline)).
- Keep the CSP strict. If inline code is ever needed, use nonces, not `unsafe-inline`.

## 3. Throughput

Move analysis to workers fed by a queue, with a transactional outbox, idempotent processing and a dead-letter queue ([infrastructure.md §6](infrastructure.md#6-scaling-target)). In addition:

- The dashboard currently checks "every field matches" with a query per label, an N+1 pattern. Replace it with one aggregate query or a denormalized column.
- Paginate the dashboard and the API lists.

## 4. Storage and records

- Implement `ImageStorage` on object storage with server-side encryption. Serve images through the existing authorization-checked endpoint, or through short-lived signed URLs.
- Apply retention to images and audit records according to the agency's records schedule.

## 5. AI pipeline

- **Versioning:** record the pipeline version, a hash of the prompt, and the model version with every `validation_result`.
- **Accuracy benchmark:** assemble a labeled set of real, consented submissions. Track precision and recall per field and per pipeline, and require model or prompt changes to pass it. Synthetic labels are no substitute.
- **Approved providers:** the cloud pipeline already sits behind `ExtractionPipeline`. Point it at FedRAMP-authorized OCR and LLM services.
- **Image quality gate:** warn when an upload is narrower than about 1000 px.
- **Field strictness:** make the stored strict, moderate and lenient settings actually adjust the comparison thresholds.

## 6. Operations

- Micrometer metrics: pipeline latency per stage, rates of fallbacks, timeouts and errors, queue depth, and how often specialists agree with the AI.
- Structured JSON logs with correlation IDs, and alerts when fallbacks or timeouts spike.
- Managed PostgreSQL with point-in-time recovery, and Flyway run as a separate deploy step.
- A restore that has actually been tested, with RPO and RTO agreed with the program office.

## 7. Notifications

Tell applicants about decisions and approaching deadlines. Use an outbox table so a message is only sent once its transaction has committed.

## 8. Compliance and accessibility

- **Section 508 / WCAG 2.1 AA:** semantic HTML, labels, visible focus, a skip link and reduced-motion support are already in place ([ui.md](ui.md#accessibility-checklist)). Add automated axe checks to CI, and do a manual screen-reader review.
- **ATO:** a system security plan, continuous monitoring, and an audit export that can't be tampered with (hash chaining or WORM storage).
- **Privacy:** a privacy impact assessment covering applicant contact details.

## 9. Roadmap

Ideas beyond the current scope:

- An interactive image viewer: pan and zoom, click a field to jump to its box, and draw annotations while reviewing.
- Record which submitted values were accepted unchanged from pre-fill, so specialists can see where an applicant relied on OCR.
- A dashboard of AI mistakes, showing the fields specialists overturn most often.
- A regulation quick-reference linked from each field.
- Keyboard shortcuts for fast review.
- Letters to applicants generated from the decision and the field findings.
