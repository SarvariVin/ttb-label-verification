# Test Scenarios

The full catalogue of functional, security, and non-functional test scenarios.

**How each scenario is verified**

- **Auto** means an automated test covers it. The *Evidence* column names the test class. All 117 tests pass with `./mvnw test`.
- **HTTP** means it was checked against a running instance with curl, using the same session, CSRF, and multipart flow a browser uses.
- **Manual** means it needs a person at a browser (visual and interaction checks). The *Result* column says "Not run" until someone executes it.

**Test data:** synthetic labels in [test-labels/](../test-labels/README.md). Bootstrap accounts: `specialist@example.gov` and `applicant@example.com`. The password comes from `APP_SEED_PASSWORD`, or from the startup log.

**Test classes**

| Class | Kind | Needs Tesseract |
|-------|------|-----------------|
| `FieldComparatorTest`, `OcrTextSearchTest` | Unit — comparison engine | No |
| `LabelFieldExtractorTest` | Unit — pre-fill extraction | No |
| `StatusDeterminerTest`, `EffectiveStatusTest`, `ExpectedFieldsTest` | Unit — verdict rules | No |
| `HealthWarningTest`, `ImageFileValidatorTest` | Unit — regulatory text, upload validation | No |
| `LabelWorkflowIntegrationTest` | Integration — full Spring context, H2, stubbed AI | No |
| `SyntheticLabelsEndToEndTest` | End-to-end — real OCR over synthetic labels | Yes (skipped if absent) |
| `RailwayProfileIntegrationTest` | Integration — `railway` profile, database image storage | No |
| `DatabaseUrlEnvironmentPostProcessorTest` | Unit — platform database URL conversion | No |
| `DemoLoginIntegrationTest` | Integration — demo-account picker and `/login/demo` | No |
| `JdbcSessionIntegrationTest` | Integration — sessions stored in the database | No |

---

## 1. Authentication and session

| ID | Scenario | Steps | Expected | Type | Evidence / Result |
|----|----------|-------|----------|------|-------------------|
| AUTH-01 | Unauthenticated page request | Open `/` without signing in | Redirect to `/login` | Auto | `LabelWorkflowIntegrationTest.unauthenticatedAccess` |
| AUTH-02 | Unauthenticated API request | `GET /api/v1/labels` without credentials | 401, no redirect | Auto | `unauthenticatedAccess` |
| AUTH-03 | API with valid Basic credentials | `GET /api/v1/labels` with specialist credentials | 200 | Auto | `unauthenticatedAccess` |
| AUTH-04 | Web sign-in, valid credentials | Submit the login form | Redirect to dashboard for the user's role | HTTP | Pass |
| AUTH-05 | Web sign-in, wrong password | Submit a wrong password | Stays on login with "Invalid email or password" | Manual | Not run |
| AUTH-06 | Sign out | Click **Sign out** | Session ended; redirect to `/login?logout` with notice | HTTP | Pass (production) |
| AUTH-07 | Bootstrap password from environment | Start with `APP_SEED_PASSWORD` set | Accounts use that password; the log does not print it | Manual | Not run |
| AUTH-08 | Generated bootstrap password | Start without `APP_SEED_PASSWORD` on an empty DB | Random password logged once at WARN | HTTP | Pass (log checked) |
| AUTH-09 | Seeding disabled | Start with `APP_SEED=false` on an empty DB | No accounts created | Manual | Not run |
| AUTH-11 | Accounts from `APP_USERS` (hashes and plain) | Start with 2 specialists and 4 applicants declared | All created with the right role and company; each can sign in; applicants get 403 on settings | Auto + HTTP | `UserProvisionerIntegrationTest`; jar run with real hashes: pass (6/6) |
| AUTH-12 | `APP_USERS` invalid entries | Weak password, unknown role, bad email | Those entries skipped with a log line; others still created | Auto | `UserProvisionerIntegrationTest` |
| AUTH-13 | `APP_USERS` idempotent, password rotation | Restart with the same value; change one password | No duplicates; only the changed password is updated | Auto | `UserProvisionerIntegrationTest` |
| AUTH-14 | Bootstrap password reset | `APP_SEED_RESET_PASSWORD=true` with a new `APP_SEED_PASSWORD` | Both bootstrap accounts get the new password; nothing happens without the flag or with an empty password | Auto | `DataSeederResetTest` |
| AUTH-15 | Demo login off by default | Default configuration | No picker on the login page; `POST /login/demo` refused | Auto | `DemoLoginIntegrationTest.DisabledByDefault` |
| AUTH-16 | Demo login on | `APP_DEMO_LOGIN=true`, optional allow-list | Picker lists allowed accounts only, never passwords; selecting one signs in; CSRF required; others refused | Auto + browser | `DemoLoginIntegrationTest.Enabled`; browser: signed in as Specialist Two, pass |
| AUTH-17 | Seeder runs before `APP_USERS` | Empty database with `APP_USERS` set | Bootstrap accounts and settings created, then the declared accounts | HTTP | Pass (log order checked) |
| AUTH-18 | Session survives a restart | Sign in, restart the app, reuse the same `SESSION` cookie | Still signed in; session row stored in `spring_session` | Auto + HTTP | `JdbcSessionIntegrationTest`; local restart: pass |
| AUTH-19 | Demo picker fills both fields | Click **Email**, select a user | Email filled, password filled with a masked placeholder, **Sign in** focused; Enter signs in | Browser | Pass (local build; list verified on production) |
| AUTH-10 | Login page has no credentials | View `/login` | No demo accounts or passwords shown | Manual | Pass (screenshot) |

## 2. Authorization and data isolation

| ID | Scenario | Steps | Expected | Type | Evidence / Result |
|----|----------|-------|----------|------|-------------------|
| AUTHZ-01 | Applicant opens Settings (web) | `GET /settings` as applicant | 403 | Auto | `roleGates` |
| AUTHZ-02 | Applicant calls Settings API | `GET /api/v1/settings` as applicant | 403 | Auto | `roleGates` |
| AUTHZ-03 | Specialist opens submission page | `GET /submit` as specialist | 403 | Auto | `roleGates` |
| AUTHZ-04 | Applicant batch-approves | `POST /api/v1/labels/batch-approve` as applicant | 403 | Auto | `roleGates` |
| AUTHZ-05 | Applicant views another company's label (API) | `GET /api/v1/labels/{foreignId}` | 404 (existence not revealed) | Auto | `applicantsCannotSeeOtherApplicantsLabels` |
| AUTHZ-06 | Applicant views another company's label (web) | `GET /labels/{foreignId}` | 404 page | Auto | `applicantsCannotSeeOtherApplicantsLabels` |
| AUTHZ-07 | Applicant fetches another company's image | `GET /images/{foreignImageId}` | 404 | Manual | Not run (same code path as AUTHZ-06) |
| AUTHZ-08 | Specialist uses pre-fill | `POST /api/v1/labels/extract` as specialist | 403 | Auto | `SyntheticLabelsEndToEndTest.prefillSecurityAndValidation` |
| AUTHZ-09 | Applicant hides AI scores | Applicant `GET /api/v1/labels/{id}` | `overallConfidence`, per-field confidence and reasoning are null | Auto | `submitThenBatchApprove` |
| AUTHZ-10 | Applicant posts a review | Applicant `POST /labels/{id}/review` | 403 | Manual | Not run |
| AUTHZ-11 | Correction linked to another company's label | Submit with a foreign `priorLabelId` | Rejected (access denied) | Manual | Not run |

## 3. Label pre-fill (read the image, fill the form)

| ID | Scenario | Steps | Expected | Type | Evidence / Result |
|----|----------|-------|----------|------|-------------------|
| PRE-01 | Spirits label fills every field | Choose `aldercrest-bourbon/front.png` | Type Distilled Spirits, 750 mL, brand, fanciful, class, ABV, net contents, phrase, address, age filled | Auto + HTTP | `prefillReadsEveryFieldOfTheBourbonLabel`; HTTP pass (10 fields, 563 ms) |
| PRE-02 | Wine label fills varietal, vintage, appellation, sulfites | Choose `quillmoor-chardonnay/front.png` | Wine, 750 mL, Chardonnay, 2022, Sonoma Coast, sulfites checked | Auto + HTTP | `prefillOtherLabels`; HTTP pass (12 fields, 312 ms) |
| PRE-03 | Malt beverage in fluid ounces | Choose `tidewater-lager/front.png` | Malt Beverages, 355 mL, class "Lager" (not the fanciful "Harbor Lager") | Auto | `prefillOtherLabels`, `LabelFieldExtractorTest.lagerPrefersThePureClassLineOverTheFancifulName` |
| PRE-04 | Health warning is never pre-filled | Any label | No health-warning value in the response | Auto | `prefillReadsEveryFieldOfTheBourbonLabel` |
| PRE-05 | Only empty fields are filled | Type a brand, then choose images | The typed brand is kept; other empty fields fill | Manual | Not run |
| PRE-06 | Pre-filled fields are highlighted | Choose images | Filled inputs are highlighted; the highlight clears when edited; the review notice appears | Manual | Not run |
| PRE-07 | "Read label again" button | Clear some fields, click the button | Cleared fields refill | Manual | Not run |
| PRE-08 | Non-image file | Upload HTML renamed `.png` | 422 with a message; the form stays usable | Auto | `prefillSecurityAndValidation` |
| PRE-09 | Missing CSRF token (web endpoint) | `POST /submit/extract` without a token | 403 | Auto | `prefillSecurityAndValidation` |
| PRE-10 | Image with no text | Upload a blank PNG | "No text could be recognised…"; nothing filled | Manual | Not run |
| PRE-11 | No OCR engine | Run without Tesseract and without cloud keys | 503 "could not be read automatically"; manual entry still works | Manual | Not run |
| PRE-12 | Multiple images | Choose front and back | Front values win; back fills the gaps | Manual | Not run |
| PRE-13 | Ampersand phrase with inline address | Line "Brewed & Bottled by Harborview Brewing, Erie, PA" | Phrase and address split correctly | Auto | `LabelFieldExtractorTest.ampersandPhraseAndInlineAddress` |
| PRE-14 | Imported spirit | "Product of Mexico" / "Imported by" | Country of origin and phrase extracted | Auto | `importedSpiritCountryOfOrigin` |
| PRE-15 | Pre-fill is not an approval shortcut | Pre-fill from the flawed label, submit unchanged | Still proposed **Rejected** (health-warning prefix) | Auto | `prefillThenSubmitOfFlawedLabelIsStillRejected` |

## 4. Single submission and analysis

| ID | Scenario | Steps | Expected | Type | Evidence / Result |
|----|----------|-------|----------|------|-------------------|
| SUB-01 | Compliant spirits label | Submit Aldercrest with its `application.json` | 201; Pending review; AI proposes **Approved**; 9/9 fields match | Auto | `SyntheticLabelsEndToEndTest` (aldercrest → APPROVED) |
| SUB-02 | Compliant wine label | Submit Quillmoor | Approved; 8/8 | Auto | same, quillmoor |
| SUB-03 | Compliant malt beverage | Submit Tidewater | Approved; 8/8 | Auto | same, tidewater |
| SUB-04 | Wrong ABV and title-case warning | Submit Northvale (application says 42%) | **Rejected**; ABV "expected 42%, found 40%"; warning "not in capital letters" | Auto | same, northvale |
| SUB-05 | Illegal container size | Spirits at 740 mL | AI proposes **Rejected** | Auto | `illegalContainerSizeIsProposedForRejection` |
| SUB-06 | Brand missing | Submit without a brand | 400 / form error "Brand Name is required" | Manual | Not run |
| SUB-07 | No images | Submit without files | "Upload at least one label image" | Manual | Not run |
| SUB-08 | More than 6 images | Submit 7 files | Rejected with a limit message | Manual | Not run |
| SUB-09 | Spoofed image | HTML/SVG content named `.png` | 422; nothing stored | Auto | `spoofedImageIsRejected` |
| SUB-10 | Image over 10 MB | Upload an 11 MB file | Rejected before storage | Manual | Not run |
| SUB-11 | Declared value not on label | Brand "xyz" on the Aldercrest image | Brand **Not found**; proposal Needs correction | HTTP | Pass (observed in session: brand not found, 50% confidence) |
| SUB-12 | Pipeline unavailable | No Tesseract, no cloud | Label saved **Pending**; message shown | Manual | Not run |
| SUB-13 | Pipeline timeout | Set `app.pipeline.timeout=1ms` | Label **Pending**, `timedOut: true`, no fallback | Manual | Not run |
| SUB-14 | Correction of an earlier label | Use **Submit correction** on a Needs-correction label | New label linked through `priorLabelId`; detail shows "Corrects …" | Manual | Not run |

## 5. Comparison rules

| ID | Scenario | Expected | Type | Evidence |
|----|----------|----------|------|----------|
| CMP-01 | Brand case/apostrophes (`STONE'S THROW` vs `Stone's Throw`) | Match, 100 | Auto | `FieldComparatorTest.Fuzzy` |
| CMP-02 | Partial OCR read of class/type | Match (containment) | Auto | `FieldComparatorTest.Fuzzy` |
| CMP-03 | Different brand | Mismatch | Auto | `FieldComparatorTest.Fuzzy` |
| CMP-04 | ABV formats (`%`, proof, `ABV`), difference under 0.5 | Match | Auto | `FieldComparatorTest.Normalized` |
| CMP-05 | ABV off by 0.5 or more (`45%` vs `40%`, `6.0%` vs `5.5%`) | Mismatch with values in reasoning | Auto | `FieldComparatorTest.Normalized` |
| CMP-06 | Net contents across units (mL, cL, L, fl oz) | Match within 1% | Auto | `FieldComparatorTest.Normalized` |
| CMP-07 | Age statement | Years compared numerically | Auto | `FieldComparatorTest.Normalized` |
| CMP-08 | Health warning exact / whitespace | Match 100 | Auto | `FieldComparatorTest.Exact` |
| CMP-09 | Health warning prefix title case | **Mismatch** (27 CFR 16.22) | Auto | `healthWarningPrefixNotInCapitalsIsMismatch` |
| CMP-10 | Health warning body all caps, prefix correct | Match | Auto | `healthWarningBodyCaseDifferenceStillMatches` |
| CMP-11 | Truncated health warning | Mismatch | Auto | `truncatedHealthWarningIsMismatch` |
| CMP-12 | Vintage digits | Match | Auto | `vintageYearComparesDigits` |
| CMP-13 | Country of origin containment | Match | Auto | `countryOfOriginContains` |
| CMP-14 | Qualifying phrase same / different | Match / Mismatch | Auto | `qualifyingPhraseComparesKnownPhrases` |
| CMP-15 | Match confidence floor | Any match ≥ 95 | Auto | `matchConfidenceIsNeverBelow95` |
| CMP-16 | Missing value | Not found, 0 | Auto | `missingValueIsNotFound` |
| CMP-17 | OCR: punctuation dropped | Found | Auto | `OcrTextSearchTest` |
| CMP-18 | OCR: garbled warning with legible body | Found (landmark + all 6 phrases) | Auto | `OcrTextSearchTest` |
| CMP-19 | OCR: prefix readable, body illegible | Not found | Auto | `OcrTextSearchTest` |
| CMP-20 | OCR: one word per line | Found | Auto | `OcrTextSearchTest` |
| CMP-21 | OCR: numeric near-miss (40% vs 42%) | OCR value kept → Mismatch | Auto | `numericFieldsKeepTheValueActuallyOnTheLabel` |
| CMP-22 | Declared health warning ignored | Statutory text always expected | Auto | `ExpectedFieldsTest` |
| CMP-24 | OCR: warning missing clause (2) | Label text returned, not the statutory text → Mismatch | Auto | `healthWarningMissingClauseTwoIsNotReportedAsTheFullText` |
| CMP-25 | OCR: garbled title-case warning prefix | Mismatch, "capital letters" | Auto | `garbledTitleCaseWarningPrefixIsStillCaught` |
| CMP-26 | OCR: similar address, different city | Not reported as the declared value → not a Match | Auto | `similarAddressIsNotReportedAsTheDeclaredOne` |
| CMP-27 | OCR: declared `5%`, label `4.5%` | Mismatch (whole numbers only) | Auto | `numberIsNotFoundInsideALongerNumber` |
| CMP-28 | OCR: `1L` on label, `1 L` declared | Match | Auto | `missingSpaceBetweenQuantityAndUnitIsTolerated` |
| CMP-23 | Accepted variant | Whitelisted pair → Match 95 | Manual | Not run (needs a row in `accepted_variants`) |

## 6. Verdict and status lifecycle

| ID | Scenario | Expected | Type | Evidence |
|----|----------|----------|------|----------|
| VER-01 | All fields match | Approved | Auto | `StatusDeterminerTest` |
| VER-02 | Health warning mismatch / missing | Rejected | Auto | `StatusDeterminerTest` |
| VER-03 | Mandatory field mismatch | Needs correction, 30 days | Auto | `StatusDeterminerTest` |
| VER-04 | Minor field only | Conditionally approved, 7 days | Auto | `StatusDeterminerTest` |
| VER-05 | Optional field not found | No effect | Auto | `StatusDeterminerTest` |
| VER-06 | ABV optional for malt beverages | Conditionally approved, not needs correction | Auto | `StatusDeterminerTest` |
| VER-07 | Standards of fill (200 mL wine vs spirits; malt unrestricted) | Rejected / allowed / allowed | Auto | `StatusDeterminerTest` |
| VER-08 | Needs correction past deadline | Rejected on next read | Auto | `EffectiveStatusTest` |
| VER-09 | Conditional approval past deadline | Needs correction with a fresh 30-day window | Auto | `EffectiveStatusTest` (transition); persistence manual |
| VER-10 | Processing longer than 5 minutes | Shown as Pending review | Auto | `EffectiveStatusTest` |
| VER-11 | Deadline urgency colours | Green > 7 d, amber 1–7 d, red < 24 h, expired | Auto | `EffectiveStatusTest` |

## 7. Specialist review

| ID | Scenario | Steps | Expected | Type | Evidence / Result |
|----|----------|-------|----------|------|-------------------|
| REV-01 | Ready-to-approve queue | Submit a compliant label | Appears under **Ready to approve** | Auto | `submitThenBatchApprove` |
| REV-02 | Batch approve | Select and approve | Approved; audit row "batch_approved" | Auto | `submitThenBatchApprove` |
| REV-03 | Batch approve of an ineligible label | Include a rejected or already-approved label | Returned in `failedIds`; others approved | Manual | Not run |
| REV-04 | Field override re-derives status | Resolve ABV as Mismatch | Needs correction | Auto | `specialistFieldReviewDerivesStatus` |
| REV-05 | Field override to Match | Resolve an unreadable warning as Match | Approved | HTTP | Pass (earlier session) |
| REV-06 | Status override needs justification | Justification under 10 characters | 400 | Auto | `overrideRequiresJustification` |
| REV-07 | Status override applied | Valid justification | 204; history shows the entry | Auto | `overrideRequiresJustification` |
| REV-08 | Override while processing | Label in Processing | Rejected with a message | Manual | Not run |
| REV-09 | Re-analyze | Click **Re-analyze** | New result is current; old one listed under analysis runs | Auto | `webFormsRequireCsrf` (happy path) |
| REV-10 | Web forms need CSRF | POST without a token | 403 | Auto | `webFormsRequireCsrf` |
| REV-11 | Overlay highlight (cloud) | Hover a field row | Its box is highlighted on the image | Manual | Not run (cloud keys needed) |

## 8. Batch CSV submission

| ID | Scenario | Expected | Type | Evidence / Result |
|----|----------|----------|------|-------------------|
| BAT-01 | Valid rows submitted | Each row linked to its label | Auto | `batchCsvSubmission` |
| BAT-02 | Missing image for a row | That row fails ("was not uploaded"); others proceed | Auto | `batchCsvSubmission` |
| BAT-03 | Invalid beverage type | Row fails with an allowed-values message | Auto | `batchCsvSubmission` |
| BAT-04 | Missing required column | Whole batch rejected, listing the columns | Manual | Not run |
| BAT-05 | More than 50 rows | Rejected with a limit message | Manual | Not run |
| BAT-06 | Sample CSV with the four synthetic labels | 4 submitted; 3 Approved, 1 Rejected | Manual | Not run |

## 9. Settings, dashboard, applicants

| ID | Scenario | Expected | Type | Evidence / Result |
|----|----------|----------|------|-------------------|
| SET-01 | Settings page renders | Pipeline availability shown | Auto | `pagesRender` |
| SET-02 | Threshold outside 50–100 | Error; nothing saved | Manual | Not run |
| SET-03 | Cloud option disabled without keys | Radio disabled with a hint | Manual | Not run |
| SET-04 | Threshold change affects the queue | Raise to 100: labels at 99% leave *Ready to approve* | Manual | Not run |
| DSH-01 | SLA cards | Queue depth, oldest item, turnaround, AI agreement with RAG colours | Auto (renders) / Manual (values) | `pagesRender` |
| DSH-02 | Applicant dashboard | Own submissions with status and deadline | Auto | `pagesRender` |
| APP-01 | Applicants list and notes | Companies with counts; notes save | Auto (renders) / Manual (save) | `pagesRender` |

## 10. Security and non-functional

| ID | Scenario | Expected | Type | Evidence / Result |
|----|----------|----------|------|-------------------|
| SEC-01 | Content Security Policy | `script-src 'self'`; eval blocked | HTTP | Pass (browser blocked `new Function` under the CSP) |
| SEC-02 | Security headers | CSP, `X-Frame-Options: DENY`, `nosniff`, HSTS, referrer policy | Manual | Not run (inspect response headers) |
| SEC-03 | No credentials in repository docs/config | None found | HTTP | Pass (repository scan) |
| SEC-04 | Path traversal in storage key | Rejected | Manual | Not run (unit-level check is in `LocalImageStorage.resolve`) |
| SEC-05 | Stack traces hidden | Error page / problem JSON without traces | Manual | Not run |
| PERF-01 | Local analysis time | < 1 s per 1600×2000 label | HTTP | Pass (0.52–0.79 s) |
| PERF-02 | Pre-fill time | < 1 s per label | HTTP | Pass (0.31–0.56 s) |
| A11Y-01 | Keyboard-only use of the submission form | All controls reachable; focus visible | Manual | Not run |
| A11Y-02 | Screen-reader status for pre-fill | Status text announced (`aria-live`) | Manual | Not run |
| A11Y-03 | Dark mode | Readable contrast | Manual | Not run |

## 11. Deployment (Railway / small container)

| ID | Scenario | Steps | Expected | Type | Evidence / Result |
|----|----------|-------|----------|------|-------------------|
| DEP-01 | Railway profile uses database image storage | Boot with `railway` profile; submit; fetch image | Image stored in `image_blobs` and served byte-identical | Auto | `RailwayProfileIntegrationTest` |
| DEP-02 | OCR concurrency limit | Boot with `railway` profile | `app.ocr.max-concurrent` = 1 | Auto | `RailwayProfileIntegrationTest` |
| DEP-03 | Platform database URL | `DATABASE_URL=postgresql://user:p%40ss@host:5432/db` | JDBC URL, user and decoded password set; explicit variables win; JDBC URLs untouched | Auto | `DatabaseUrlEnvironmentPostProcessorTest` |
| DEP-04 | Fits 512 MB | Run with the Dockerfile `JAVA_OPTS`; 4 pre-fills, 4 + 8 concurrent submissions | Peak memory < 512 MB; all verdicts correct; health UP | HTTP | Pass (peak 402 MB, macOS) |
| DEP-05 | Startup time under constrained flags | Start the jar with the Dockerfile `JAVA_OPTS` | Started, health UP | HTTP | Pass (3.5 s) |
| DEP-06 | Docker image builds | `docker build .` / Railway build | Image builds; app starts as non-root with Java 21 | Manual | Pass (Railway build log: Java 21.0.12, `/app/app.jar`, `appuser`) |
| DEP-10 | Missing `DATABASE_URL` on Railway | Start with a Railway marker variable and no `DATABASE_URL` | Stops at once with "DATABASE_URL is not set on this Railway service" and the fix | Auto + HTTP | `DatabaseUrlEnvironmentPostProcessorTest`; jar run: pass |
| DEP-11 | Railway profile switches on automatically | Railway marker + `DATABASE_URL`, no profile | "profile is active: railway"; redirects use `https` behind proxy headers | Auto + HTTP | `DatabaseUrlEnvironmentPostProcessorTest`; jar run: pass |
| DEP-07 | Railway deploy end to end | Follow [deploy-railway.md](deploy-railway.md) | Health UP over HTTPS; submission works; image displays; memory < 512 MB in Railway metrics | Manual | Not run |
| DEP-08 | HTTPS redirects behind proxy | Sign in on the Railway domain | Redirects stay on `https://`; session cookie `Secure` | Manual | Not run |
| DEP-09 | Sleep and wake | Leave idle until sleeping, then open | First request waits for startup, then works; users stay signed in (sessions in the database) | Manual | Not run |

## 12. Production sample run (34 labels)

Synthetic labels were generated for this run and sent to the deployed Railway application, first through OCR pre-fill and then through a full submission with the declared values. Details are in [ai-pipelines.md](ai-pipelines.md#production-sample-run-34-labels).

| ID | Scenario | Labels | Expected | Type | Result |
|----|----------|--------|----------|------|--------|
| PRD-01 | Clean labels, varied products, fonts and colours | 14 | Approved; pre-fill ≥ 6 values; beverage type detected | HTTP | 13/14 before the fixes (`1L` not matched); 14/14 after |
| PRD-02 | Clean labels, degraded images (rotation, blur, JPEG, 640 px, noise, low contrast, light-on-dark, monospace) | 9 | Approved | HTTP | 9/9 |
| PRD-03 | Deliberately flawed labels (warning absent, title case or truncated; illegal size; ABV, net contents, address, fanciful name mismatches; no sulfite line) | 11 | Rejected / Needs correction / Conditionally approved per the rules | HTTP | 7/11 before the fixes; 10/11 after. The remaining one follows the documented rule that a missing optional field is ignored. |

OCR took 0.5–0.9 s per image on the 512 MB container. The results after the fixes were measured on a local build; re-run against production once the fixes are deployed.

---

## Running

```bash
./mvnw test
```

```bash
./mvnw test -Dtest=SyntheticLabelsEndToEndTest
```

For manual scenarios, start the demo profile, read the bootstrap password from the log, and use the synthetic labels:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

## Summary

Some scenarios are both automated and verified live, so the columns overlap.

| Area | Scenarios | Automated | Verified live (HTTP / browser / Railway) | Not yet run |
|------|-----------|-----------|------------------------------------------|-------------|
| Authentication | 19 | 10 | 9 | 3 |
| Authorization | 11 | 8 | 0 | 3 |
| Pre-fill | 15 | 9 | 2 | 6 |
| Submission | 14 | 6 | 1 | 7 |
| Comparison | 28 | 27 | 0 | 1 |
| Verdict / lifecycle | 11 | 11 | 0 | 0 |
| Review | 11 | 7 | 1 | 3 |
| Batch | 6 | 3 | 0 | 3 |
| Settings / dashboard / applicants | 7 | 4 | 0 | 3 |
| Security / non-functional | 10 | 0 | 4 | 6 |
| Deployment | 11 | 5 | 5 | 3 |
| Production sample run | 3 | 0 | 3 | 0 |
| **Total** | **146** | **90** | **25** | **38** |
