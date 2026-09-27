# REST API Reference

Base path: `/api/v1`. Authentication: **HTTP Basic**, stateless. Sessions and cookies are ignored on this path, so no CSRF token is needed.

The examples read credentials from environment variables, so no secret ever appears in a command line you might share:

```bash
export API_USER=applicant@example.com API_PASSWORD='<your password>'
```

Errors use [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `application/problem+json`:

```json
{ "type": "about:blank", "title": "Unprocessable Entity", "status": 422,
  "detail": "front.png: file content is not a JPEG, PNG or WebP image", "instance": "/api/v1/labels" }
```

| Status | Meaning |
|--------|---------|
| 400 | Bean Validation failed (`detail` lists `field: message`) |
| 401 | Missing or invalid credentials |
| 403 | Authenticated but wrong role |
| 404 | Not found, **or** not visible to the caller (applicants never learn other companies' label IDs exist) |
| 422 | Business rule violated (bad image, ineligible state, CSV errors…) |

Enum values are upper-case: `DISTILLED_SPIRITS | WINE | MALT_BEVERAGE`, `PENDING | PROCESSING | PENDING_REVIEW | APPROVED | CONDITIONALLY_APPROVED | NEEDS_CORRECTION | REJECTED`, `MATCH | MISMATCH | NOT_FOUND | NEEDS_CORRECTION`.

---

## Submit a label (applicant)

`POST /api/v1/labels` — `multipart/form-data`

| Part | Required | Notes |
|------|----------|-------|
| `images` (repeat) | yes | 1–6 files, JPEG/PNG/WebP, ≤ 10 MB each; the first is treated as the front |
| `beverageType` | yes | |
| `containerSizeMl` | yes | positive integer |
| `brandName` | yes | |
| `fancifulName`, `classType`, `classTypeCode`, `serialNumber`, `alcoholContent`, `netContents`, `nameAndAddress`, `qualifyingPhrase`, `countryOfOrigin`, `grapeVarietal`, `appellationOfOrigin`, `vintageYear`, `sulfiteDeclaration` (bool), `ageStatement`, `stateOfDistillation` | no | |
| `priorLabelId` | no | Links a correction to an earlier label of the same applicant |
| `healthWarning` | no | Stored for the record only; the label is always verified against the statutory text |

```bash
curl -u "$API_USER:$API_PASSWORD" -X POST http://localhost:8080/api/v1/labels \
  -F images=@test-labels/quillmoor-chardonnay/front.png \
  -F beverageType=WINE -F containerSizeMl=750 \
  -F brandName="Quillmoor Cellars" -F classType="Chardonnay" \
  -F alcoholContent="13.5% Alc. by Vol." -F netContents="750 mL" \
  -F appellationOfOrigin="Sonoma Coast"
```

`201 Created` when analysis completed; `202 Accepted` when the label was saved but analysis failed or timed out (`error` explains, `timedOut` says which):

```json
{ "labelId": "XFtDvtaaJitdrukTU8jTW", "status": "PENDING_REVIEW", "aiProposedStatus": "APPROVED",
  "overallConfidence": 99, "error": null, "timedOut": false }
```

## Pre-fill suggestions (applicant)

`POST /api/v1/labels/extract` — `multipart/form-data` with one or more `images` parts. Reads the label and returns suggested Form 5100.31 values. Nothing is stored.

```bash
curl -u "$API_USER:$API_PASSWORD" -X POST http://localhost:8080/api/v1/labels/extract \
  -F images=@test-labels/aldercrest-bourbon/front.png
```

```json
{ "beverageType": "DISTILLED_SPIRITS", "containerSizeMl": 750, "sulfiteDeclaration": null,
  "fields": { "brandName": "ALDERCREST", "fancifulName": "Small Batch",
              "classType": "Kentucky Straight Bourbon Whiskey", "alcoholContent": "45% Alc./Vol. (90 Proof)",
              "netContents": "750 mL", "qualifyingPhrase": "Distilled and Bottled by",
              "nameAndAddress": "Aldercrest Distilling Co., Bardstown, Kentucky", "ageStatement": "Aged 6 Years" },
  "filledCount": 10, "source": "tesseract-local", "processingTimeMs": 563 }
```

Keys in `fields` match the submission parameters, so a client can send them straight to `POST /api/v1/labels` after the applicant confirms them. The health warning is never suggested; it is always verified against the statutory text. Errors: 422 (not an image or too many images), 503 (no OCR engine available).

The web form uses the same logic at `POST /submit/extract` (session + CSRF).

## List labels

`GET /api/v1/labels?queue=ready|review|all`

- Specialists: `ready` (Ready to approve), `review` (Needs review), or `all` (default).
- Applicants: always their own submissions; `queue` is ignored.

```json
[{ "id": "XFtD…", "brandName": "Quillmoor Cellars", "beverageType": "WINE", "applicant": "Sample Distilling Co.",
   "status": "PENDING_REVIEW", "aiProposedStatus": "APPROVED", "overallConfidence": 99,
   "readyToApprove": true, "createdAt": "2026-09-24T01:40:12Z", "deadlineDaysRemaining": null }]
```

## Label detail

`GET /api/v1/labels/{id}`

```json
{ "id": "XFtD…", "beverageType": "WINE", "containerSizeMl": 750, "status": "PENDING_REVIEW",
  "aiProposedStatus": "APPROVED", "overallConfidence": 99.0, "correctionDeadline": null,
  "applicant": "Sample Distilling Co.", "priorLabelId": null,
  "applicationData": { "brand_name": "Quillmoor Cellars", "…": "…" },
  "images": [{ "id": "img…", "url": "/api/v1/images/img…", "imageType": "FRONT", "filename": "front.png" }],
  "modelUsed": "tesseract-local", "processingTimeMs": 555,
  "fields": [{ "validationItemId": "vi…", "fieldName": "HEALTH_WARNING",
               "expectedValue": "GOVERNMENT WARNING: …", "extractedValue": "GOVERNMENT WARNING: (1) According to…",
               "status": "MATCH", "confidence": 100.0, "reasoning": "health_warning matches exactly after whitespace normalization.",
               "imageId": "img…", "boundingBox": null }],
  "createdAt": "…" }
```

For **applicants**, `aiProposedStatus`, `overallConfidence`, and each field's `confidence` and `reasoning` are `null`.

## Field review (specialist)

`POST /api/v1/labels/{id}/review` — allowed when status is `PENDING_REVIEW`, `NEEDS_CORRECTION`, `CONDITIONALLY_APPROVED` or `PROCESSING`.

```json
{ "overrides": [
    { "validationItemId": "vi…", "resolvedStatus": "MATCH", "reviewerNotes": "Verified visually; OCR could not read the small print" }
] }
```

`resolvedStatus` ∈ `MATCH | MISMATCH | NOT_FOUND`. Items not listed keep their status. An empty list re-derives the status from the AI results as they stand, which accepts the AI's field findings. Response:

```json
{ "status": "APPROVED" }
```

## Status override (specialist)

`POST /api/v1/labels/{id}/override` → `204 No Content`

```json
{ "newStatus": "NEEDS_CORRECTION", "justification": "Net contents on label is 700 mL, application says 750 mL", "reasonCode": "net_contents" }
```

`newStatus` ∈ `APPROVED | CONDITIONALLY_APPROVED | NEEDS_CORRECTION | REJECTED`. `justification` must be ≥ 10 characters. The status can't be overridden while the label is `PENDING`/`PROCESSING`, or set to its current value. Correction deadlines are set automatically (7 or 30 days).

## Batch approve (specialist)

`POST /api/v1/labels/batch-approve`

```json
{ "labelIds": ["a…", "b…"] }
```

At most 100 IDs. Each label is re-checked server-side (pending review, confidence ≥ threshold, all fields match), and ineligible ones are returned in `failedIds`:

```json
{ "approvedCount": 1, "failedIds": ["b…"] }
```

## Re-analyze (specialist)

`POST /api/v1/labels/{id}/reanalyze` runs the pipeline again with current settings. The previous result stays in history.

```json
{ "validationResultId": "vr…", "proposedStatus": "APPROVED", "overallConfidence": 97, "modelUsed": "tesseract-local" }
```

## Images

`GET /api/v1/images/{id}` returns the raw bytes with the stored content type and `Cache-Control: no-store`. The same data scoping as labels applies.

## Settings (specialist)

`GET /api/v1/settings` · `PUT /api/v1/settings`

```json
{ "pipelineModel": "local", "approvalThreshold": 90,
  "reviewResponseHours": 48, "totalTurnaroundHours": 72, "maxQueueDepth": 50,
  "cloudAvailable": false, "localAvailable": true }
```

`cloudAvailable` and `localAvailable` are read-only and ignored on `PUT`. `pipelineModel` ∈ `local | cloud`; `approvalThreshold` is 50–100.

## Health

`GET /actuator/health` (unauthenticated) → `{"status":"UP"}`
