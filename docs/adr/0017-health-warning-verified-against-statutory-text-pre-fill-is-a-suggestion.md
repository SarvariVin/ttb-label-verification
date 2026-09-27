# ADR-0017: Health warning verified against statutory text; pre-fill is a suggestion

- **Status:** Accepted
- **Date:** 2026-09-23

## Context

Applicants asked for the submission form to be filled automatically from the label image. Pre-fill copies text *from the label*, so if the applicant accepts it unchanged, the label is compared with itself. For most fields that is the applicant's responsibility: they must confirm each value against their approved application. For the health warning it would be a compliance hole. The warning's wording and capitalization are fixed by 27 CFR Part 16, and a defective warning copied into the form would "match" itself. The same hole existed before pre-fill whenever an applicant typed a non-compliant warning.

## Decision

- Verify the health warning **always** against the statutory text (`HealthWarning.FULL_TEXT`). A declared value is stored for the record but never used as the reference. The form no longer asks for it.
- Add pre-fill (`POST /submit/extract`, `POST /api/v1/labels/extract`): local OCR lines are processed by the rule-based `LabelFieldExtractor`, or by the cloud pipeline when selected. Only **empty** fields are filled, they are highlighted, and a notice tells the applicant to confirm each value against the application. The health warning is never pre-filled. Nothing is stored.

## Consequences

- Applicants no longer re-type what is printed on the label.
- A defective warning is caught even when every other value was accepted from pre-fill (covered by `SyntheticLabelsEndToEndTest.prefillThenSubmitOfFlawedLabelIsStillRejected`).
- Other fields still depend on the applicant's confirmation. A follow-up is to record which values were accepted unchanged, so specialists can see them ([production.md](../production.md#9-roadmap)).
- Rule-based extraction can miss fields on decorative or low-resolution labels; those stay empty for manual entry.

## Alternatives considered

- Pre-fill including the health warning: reopens the hole described above.
- Cloud-only pre-fill: unavailable in restricted deployments ([ADR-0004](0004-local-first-ocr-with-optional-cloud-ai-behind-a-pipeline-interface.md)).
