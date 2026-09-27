# REST API Reference

Everything lives under `/api/v1`. Callers authenticate with **HTTP Basic** on every request. The API chain is stateless: it never creates a session or reads a cookie, so CSRF tokens aren't involved.

The examples read credentials from environment variables, so a command you copy or share never contains a secret:

```bash
export API_USER=applicant@example.com API_PASSWORD='<your password>'
```

## Conventions

**Errors** follow [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) and are returned as `application/problem+json`:

```json
{ "type": "about:blank", "title": "Unprocessable Entity", "status": 422,
  "detail": "front.png: file content is not a JPEG, PNG or WebP image", "instance": "/api/v1/labels" }
```

| Status | When you get it |
|--------|-----------------|
| 400 | Bean Validation rejected the input. `detail` lists each `field: message`. |
| 401 | No credentials, or wrong ones |
| 403 | Signed in, but the role isn't allowed |
| 404 | The resource doesn't exist, **or** the caller isn't allowed to see it. An applicant can't tell whether another company's label ID exists. |
| 422 | A business rule failed: an unusable image, a label in the wrong state, CSV errors, and so on |

**Enums** are upper case:

- Beverage type: `DISTILLED_SPIRITS | WINE | MALT_BEVERAGE`
- Label status: `PENDING | PROCESSING | PENDING_REVIEW | APPROVED | CONDITIONALLY_APPROVED | NEEDS_CORRECTION | REJECTED`
- Field status: `MATCH | MISMATCH | NOT_FOUND | NEEDS_CORRECTION`

**Who can call what**

| Endpoint | Applicant | Specialist |
|----------|:---------:|:----------:|
| `POST /labels`, `POST /labels/extract` | ✓ | |
| `GET /labels`, `GET /labels/{id}`, `GET /images/{id}` | ✓ (own company only) | ✓ |
| `POST /labels/{id}/review`, `/override`, `/reanalyze`, `POST /labels/batch-approve` | | ✓ |
| `GET` / `PUT /settings` | | ✓ |

---

## Applicant endpoints

### Submit a label

`POST /api/v1/labels` with `multipart/form-data`

| Part | Required | Notes |
|------|----------|-------|
| `images` (repeatable) | yes | 1 to 6 files, JPEG, PNG or WebP, up to 10 MB each. The first file is treated as the front. |
| `beverageType` | yes | |
| `containerSizeMl` | yes | A positive whole number |
| `brandName` | yes | |
| `fancifulName`, `classType`, `classTypeCode`, `serialNumber`, `alcoholContent`, `netContents`, `nameAndAddress`, `qualifyingPhrase`, `countryOfOrigin`, `grapeVarietal`, `appellationOfOrigin`, `vintageYear`, `sulfiteDeclaration` (boolean), `ageStatement`, `stateOfDistillation` | no | |
| `priorLabelId` | no | Marks this as a correction of an earlier label from the same applicant |
| `healthWarning` | no | Kept for the record only. The label is always checked against the statutory text. |

```bash
curl -u "$API_USER:$API_PASSWORD" -X POST http://localhost:8080/api/v1/labels \
  -F images=@test-labels/quillmoor-chardonnay/front.png \
  -F beverageType=WINE -F containerSizeMl=750 \
  -F brandName="Quillmoor Cellars" -F classType="Chardonnay" \
  -F alcoholContent="13.5% Alc. by Vol." -F netContents="750 mL" \
  -F appellationOfOrigin="Sonoma Coast"
```

The response is **201 Created** when analysis finished. It is **202 Accepted** when the label was saved but analysis failed or ran out of time: `error` says why, and `timedOut` says which of the two happened.

```json
{ "labelId": "XFtDvtaaJitdrukTU8jTW", "status": "PENDING_REVIEW", "aiProposedStatus": "APPROVED",
  "overallConfidence": 99, "error": null, "timedOut": false }
```

### Get pre-fill suggestions

`POST /api/v1/labels/extract` with `multipart/form-data` and one or more `images` parts. It reads the label and suggests Form 5100.31 values. Nothing is saved.

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

The keys in `fields` have the same names as the submission parameters, so a client can pass them straight to `POST /api/v1/labels` once the applicant has confirmed them. The health warning is never suggested; it is always checked against the statutory text. The call returns **422** for a file that isn't an image or for too many images, and **503** when no OCR engine is available.

The web form uses the same logic at `POST /submit/extract` (session plus CSRF).

---

## Shared read endpoints

### List labels

`GET /api/v1/labels?queue=ready|review|all`

- **Specialists** pick a queue: `ready` (Ready to approve), `review` (Needs review), or `all`, which is the default.
- **Applicants** always get their own company's submissions, and `queue` is ignored.

```json
[{ "id": "XFtD…", "brandName": "Quillmoor Cellars", "beverageType": "WINE", "applicant": "Sample Distilling Co.",
   "status": "PENDING_REVIEW", "aiProposedStatus": "APPROVED", "overallConfidence": 99,
   "readyToApprove": true, "createdAt": "2026-09-27T01:40:12Z", "deadlineDaysRemaining": null }]
```

### Label detail

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

For **applicants**, `aiProposedStatus`, `overallConfidence`, and each field's `confidence` and `reasoning` come back as `null`. Scoring stays internal to TTB.

### Images

`GET /api/v1/images/{id}` streams the stored bytes with their content type and `Cache-Control: no-store`. It follows the same visibility rules as labels.

---

## Specialist endpoints

### Review individual fields

`POST /api/v1/labels/{id}/review` works while the label is `PENDING_REVIEW`, `NEEDS_CORRECTION`, `CONDITIONALLY_APPROVED` or `PROCESSING`.

```json
{ "overrides": [
    { "validationItemId": "vi…", "resolvedStatus": "MATCH", "reviewerNotes": "Verified visually; OCR could not read the small print" }
] }
```

`resolvedStatus` is one of `MATCH | MISMATCH | NOT_FOUND`. Fields you leave out keep their current status. Sending an empty list works the status out again from the AI's findings as they stand, which amounts to accepting them. The response has the new status:

```json
{ "status": "APPROVED" }
```

### Override the label status

`POST /api/v1/labels/{id}/override` returns **204 No Content**.

```json
{ "newStatus": "NEEDS_CORRECTION", "justification": "Net contents on label is 700 mL, application says 750 mL", "reasonCode": "net_contents" }
```

- `newStatus` is one of `APPROVED | CONDITIONALLY_APPROVED | NEEDS_CORRECTION | REJECTED`.
- `justification` needs at least 10 characters. It becomes part of the audit trail.
- You can't override a label that is `PENDING` or `PROCESSING`, or set the status it already has.
- Correction deadlines (7 or 30 days) are set automatically.

### Batch approve

`POST /api/v1/labels/batch-approve`

```json
{ "labelIds": ["a…", "b…"] }
```

It accepts up to 100 IDs. The server checks each label again (pending review, confidence at or above the threshold, every field a match). Labels that fail the check are listed in `failedIds` and left untouched:

```json
{ "approvedCount": 1, "failedIds": ["b…"] }
```

### Re-analyze

`POST /api/v1/labels/{id}/reanalyze` runs the pipeline again with the current settings. The earlier result stays in the history.

```json
{ "validationResultId": "vr…", "proposedStatus": "APPROVED", "overallConfidence": 97, "modelUsed": "tesseract-local" }
```

### Settings

`GET /api/v1/settings` and `PUT /api/v1/settings`

```json
{ "pipelineModel": "local", "approvalThreshold": 90,
  "reviewResponseHours": 48, "totalTurnaroundHours": 72, "maxQueueDepth": 50,
  "cloudAvailable": false, "localAvailable": true }
```

- `pipelineModel` is `local` or `cloud`.
- `approvalThreshold` must be between 50 and 100.
- `cloudAvailable` and `localAvailable` are read-only, and `PUT` ignores them.

---

## Health

`GET /actuator/health` needs no authentication and returns `{"status":"UP"}`.
