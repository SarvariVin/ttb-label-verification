# User Flows

End-to-end workflows for both roles, and the edge cases each one handles.

## Signing in

- **Normal sign-in:** type your email and password, then click **Sign in**.
- **Demo mode** (when `APP_DEMO_LOGIN=true`, as on the production demo site):
  1. Click the **Email** field and pick an account from the **Demo accounts** list.
  2. The email and password fill in automatically. The password is a masked placeholder; no real password is sent to the browser.
  3. **Sign in** is highlighted, so press Enter.

  Editing either field afterwards switches back to a normal password check.
- **Staying signed in:** sessions last 8 hours and are stored in the database, so a restart, deploy or sleep/wake of the app doesn't sign you out.

---

## Applicant

### 1. Single submission

1. Sign in with the applicant account. The dashboard lists this company's submissions.
2. Open **New submission**.
3. Choose 1–6 label images, front first (JPEG or PNG; WebP with the cloud pipeline).
4. **Pre-fill.** The label is read at once (under a second locally). Empty fields are filled and highlighted: type of product, capacity, brand, fanciful name, class/type, alcohol content, net contents, qualifying phrase, name and address, and, where present, age, vintage, appellation, varietal, country of origin and sulfites. A notice asks the applicant to check each value against their approved application. **Read label again** re-runs it; fields already typed are never overwritten.
5. Correct anything that differs from the application, and complete fields the label reader missed. The health warning is not entered; it is always verified against the statutory text.
6. **Submit for verification.** The button shows *Analyzing label…* while the pipeline runs (under a second locally).
7. The label page opens with status **Pending review**, the images, and a field-by-field comparison of *Application* against *Label*. Applicants see field statuses, but not AI confidence or reasoning.

### 2. Batch submission (CSV)

1. Open **Batch upload**.
2. Choose a CSV (see [test-labels/batch-example.csv](../test-labels/batch-example.csv)) and every image it references.
3. Each row is validated and submitted independently. The results table lists every row: submitted rows link to their label, and failed rows say why (for example, "Image "x.png" was not uploaded").
4. Limit: 50 rows per batch.

### 3. Correcting a label

1. A label marked **Needs correction** (30 days) or **Conditionally approved** (7 days) shows a deadline badge and a **Submit correction** button.
2. The correction is linked to the original label (`prior_label_id`) and goes through the full pipeline.
3. If the deadline passes, the next read downgrades the label. Conditionally approved becomes needs correction, with a new 30-day window. Needs correction becomes rejected.

---

## Specialist

### 4. Working the queue

1. Sign in with the specialist account. The dashboard shows SLA cards (queue depth, oldest item, average turnaround, AI agreement) and three tabs.
2. **Ready to approve** holds labels that are pending review, where the AI proposes approval, every field matches, and confidence meets the threshold. Spot-check some, select them, and **Approve selected**. Each label is re-validated on the server before approval.
3. **Needs review** holds every other label awaiting a decision, oldest first.

### 5. Field-by-field review

1. Open a label from *Needs review*. A banner shows the AI recommendation and confidence.
2. Each row shows the field, its status, confidence, the application value, the label value, and the reasoning. With the cloud pipeline, hovering a row highlights its box on the image.
3. For any field the AI got wrong, choose **Resolve as** Match, Mismatch, or Not found, and optionally add a note.
4. **Save review & derive status.** Each changed field is recorded in `human_reviews`, and the label status is re-derived. Deadlines are set automatically.

*Example:* a low-resolution photo makes the health warning unreadable, so the AI proposes **Rejected**. The specialist checks the image, resolves *Health warning* as Match, and the label becomes **Approved**.

*Example:* the synthetic Northvale label is proposed **Rejected**. The comparison shows alcohol content `40%` on the label against `42%` declared, and a warning prefix that is not in capitals. The specialist confirms and sets **Needs correction** with a justification.

### 6. Setting the final status

**Set final status** accepts any decision with a justification of at least 10 characters, and writes a `status_overrides` audit row. It is blocked while the label is processing, and when the decision equals the current status.

### 7. Re-analyze

After a pipeline change in **Settings**, or for a label left `PENDING` by a failed analysis, **Re-analyze** runs the pipeline again. The previous result is kept and marked superseded.

### 8. Settings and applicants

- **Settings:** pipeline (local or cloud, with detected availability), approval threshold (50–100%), SLA targets.
- **Applicants:** companies, contacts, label counts, and editable specialist notes.

---

## Edge cases

| Scenario | Behavior |
|----------|----------|
| File named `.png` but containing HTML/SVG | Rejected by the magic-byte check (422 / form error); nothing stored |
| Image over 10 MB | Rejected before storage |
| Illegal container size (740 mL spirits, 200 mL wine) | AI proposes **Rejected** regardless of text |
| Alcohol content differs by a digit (`40%` vs `42%`) | Mismatch; OCR text is kept for numeric fields |
| Health warning prefix not in capitals | Mismatch → **Rejected** proposal |
| Health warning body in all caps, prefix correct | Match |
| Alcohol content differs by half a point or more (`6.0%` vs `5.5%`) | Mismatch |
| Declared number appears only inside a longer one (`5%` vs label `4.5%`) | Mismatch; matches must be whole numbers |
| Health warning missing a clause | Mismatch → **Rejected** proposal; all six key phrases must be legible |
| Similar but different address (`…Portland, Maine` vs declared `…Austin, Texas`) | Mismatch; near misses are compared as they read on the label |
| OCR drops punctuation or spaces (`STONES THROW`, `1L`) | Match through space- and punctuation-insensitive search |
| Decorative label with one word per line | Match through scattered-word search (text fields only) |
| No OCR engine available | Label saved **Pending** with an explanation; Settings shows the pipeline as unavailable |
| Pipeline over 60 s | Label saved **Pending** (`timedOut: true`); no fallback |
| Server crash during analysis | Label shown as **Pending review** after 5 minutes |
| Applicant requests another company's label or image | 404 |
| Applicant calls a specialist endpoint | 403 |
| Two specialists batch-approve the same label | The second sees it in `failedIds` |
| CSV row references a missing image | That row fails; the other rows proceed |
