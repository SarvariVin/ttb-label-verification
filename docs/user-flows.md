# User Flows

A walk through each role's work from start to finish, followed by the unusual situations the system is built to handle. Screen layout and components are described in the [UI guide](ui.md).

## Signing in

The sign-in page is split in two. On the left, a brand panel sums up the service in three steps: read, compare, decide. On the right is the form. On a phone, the brand panel shrinks to a header above the form.

- **Normal sign-in:** enter your email and password, then choose **Sign in**. A spinner shows while the request is running.
- **Demo mode** (when `APP_DEMO_LOGIN=true`):
  1. Click **Email**, and the **Demo accounts** list opens.
  2. Choose an account. The email and a masked placeholder password fill in. No real password is sent to the browser.
  3. **Sign in** is highlighted, so press Enter.

  If you change either field afterwards, the form goes back to a normal password check.
- **Staying signed in:** a session lasts 8 hours and is stored in the database, so restarts, deploys and sleep/wake cycles don't sign you out.

After signing in, the top bar shows the pages your role can use. The current page is highlighted, and your initials and role appear on the right next to **Sign out**.

Out of the box there are three accounts to try: a **Labeling Specialist** and two applicants from different companies (**Test Applicant One**, Sample Distilling Co., and **Test Applicant Two**, Sample Winery LLC). See [user-accounts.md](user-accounts.md#bootstrap-accounts).

---

## Applicant

### 1. Submitting one label

1. Sign in as an applicant. **My submissions** opens with four summary tiles (total, in review, action needed, approved) above your company's list.
2. Choose **New submission**. The form has four numbered steps: label images, product, label text, and wine/spirits details.
3. In step 1, pick 1 to 6 images, front first. JPEG or PNG work everywhere; WebP needs the cloud pipeline.
4. **Pre-fill** starts straight away and takes under a second locally. Empty fields are filled in and highlighted: product type, capacity, brand, fanciful name, class/type, alcohol content, net contents, qualifying phrase, name and address, and, when present, age, vintage, appellation, varietal, country of origin and sulfites. A notice asks you to check each value against your approved application. **Read label again** runs it once more, and it never overwrites a field you have typed in.
5. Fix anything that differs from the application, and fill in anything the reader missed. You don't enter the health warning; it is always checked against the statutory text.
6. Choose **Submit for verification**. The submit bar stays at the bottom of the screen on long forms. The button shows *Analyzing label…* while the check runs.
7. The label page opens with the status **Pending review**, the images, and a field-by-field *Application* versus *Label* comparison. Applicants see each field's outcome, but not the AI's confidence or reasoning.

### 2. Submitting a batch (CSV)

1. Choose **Batch upload**.
2. Pick a CSV file (see [test-labels/batch-example.csv](../test-labels/batch-example.csv)) and every image it names.
3. Each row is checked and submitted on its own. The results table lists every row: submitted rows link to their label, and failed rows explain what went wrong, for example *Image "x.png" was not uploaded*.
4. A batch can have up to 50 rows.

### 3. Correcting a label

1. A label marked **Needs correction** (30 days) or **Conditionally approved** (7 days) shows a deadline badge and a **Submit correction** button.
2. The correction is linked to the original through `prior_label_id` and goes through the full check.
3. If the deadline passes, the label is downgraded the next time it is read. *Conditionally approved* becomes *Needs correction* with a fresh 30 days, and *Needs correction* becomes *Rejected*.

---

## Specialist

### 4. Working the queues

1. Sign in as the specialist. The **Review dashboard** shows four SLA tiles (queue depth, oldest in queue, average turnaround, AI agreement). Each has a colored stripe for on target, at risk or breached. Below them are three tabs.
2. **Ready to approve** lists labels that are pending review, where the AI proposes approval, every field matches, and confidence meets the threshold. Spot-check a few, tick them, and choose **Approve selected**. The server checks every label again before approving it.
3. **Needs review** holds everything else awaiting a decision, oldest first. **All labels** shows the full list.

### 5. Reviewing field by field

1. Open a label from *Needs review*. A banner shows the AI's recommendation and confidence, and the card header summarizes the result, for example *5 of 7 fields match*.
2. Each row has a colored edge for its status and shows the field name, status, confidence, the application value, the label value, and the AI's reasoning. With the cloud pipeline, hovering over a row outlines that field on the image. On a wide screen the image stays in view while you scroll the findings. On a phone, the rows stack.
3. Wherever the AI got a field wrong, set **Resolve as** to Match, Mismatch or Not found, and add a note if you want to.
4. Choose **Save review & derive status**. Each change is recorded in `human_reviews`, the label's status is worked out again, and deadlines are set automatically.

*Example:* a low-resolution photo leaves the health warning unreadable, so the AI proposes **Rejected**. The specialist looks at the image, resolves *Health warning* as Match, and the label becomes **Approved**.

*Example:* the synthetic Northvale label comes in as **Rejected**. The comparison shows `40%` alcohol on the label against `42%` declared, and a warning prefix that isn't in capitals. The specialist agrees and sets **Needs correction** with a justification.

### 6. Setting the final status

**Set final status** accepts any decision with a justification of at least 10 characters, and writes a row to `status_overrides`. It isn't available while the label is processing, or when the chosen decision matches the current status.

### 7. Re-analyzing

After changing the pipeline in **Settings**, or for a label left **Pending** by a failed analysis, choose **Re-analyze** to run the check again. The earlier result is kept and marked as superseded, and it appears under *Analysis runs*.

### 8. Settings and applicants

- **Settings:** which pipeline to use (local or cloud, with detected availability), the approval threshold (50–100%), and the SLA targets.
- **Applicants:** each company with its contact, number of labels, and private notes for the review team, which specialists can edit.

---

## Edge cases

| Situation | What happens |
|-----------|--------------|
| A `.png` file that actually contains HTML or SVG | The magic-byte check rejects it (422, or a form error), and nothing is stored |
| An image over 10 MB | Rejected before anything is stored |
| An illegal container size (740 mL spirits, 200 mL wine) | The AI proposes **Rejected**, whatever the text says |
| Alcohol content off by a digit (`40%` against `42%`) | Mismatch. Numeric fields keep the OCR reading. |
| Alcohol content off by half a point or more (`6.0%` against `5.5%`) | Mismatch |
| A declared number found only inside a longer one (`5%` against a label's `4.5%`) | Mismatch. Numbers must match whole. |
| Health-warning prefix not in capitals | Mismatch, so the proposal is **Rejected** |
| Warning body in capitals with a correct prefix | Match |
| The warning is missing a clause | Mismatch, so the proposal is **Rejected**. All six key phrases must be readable. |
| A similar but different address (`…Portland, Maine` against `…Austin, Texas`) | Mismatch. Near misses are judged on what the label says. |
| OCR loses spaces or punctuation (`STONES THROW`, `1L`) | Match, through the space- and punctuation-insensitive search |
| A decorative label with one word per line | Match, through the scattered-word search (text fields only) |
| No OCR engine available | The label is saved as **Pending** with an explanation, and Settings shows the pipeline as unavailable |
| Analysis takes longer than 60 s | The label is saved as **Pending** (`timedOut: true`), and the other pipeline isn't tried |
| The server crashes mid-analysis | The label appears as **Pending review** after 5 minutes |
| An applicant asks for another company's label or image | 404 |
| An applicant calls a specialist endpoint | 403 |
| Two specialists batch-approve the same label | The second one gets it back in `failedIds` |
| A CSV row names an image that wasn't uploaded | That row fails, and the rest go ahead |
| The page is opened on a phone | Nav scrolls sideways, stat tiles go two per row, and nothing scrolls horizontally |
