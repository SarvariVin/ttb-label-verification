# ADR-0012: Numeric fields keep OCR text; warning prefix must be capitals

- **Status:** Accepted
- **Date:** 2026-09-23

## Context

OCR text search treats a strong fuzzy hit as "present, differences are OCR noise" and returns the declared value. For numeric fields that masks real violations: a label reading **40%** matched an application declaring **42%**. Separately, a health warning with a title-case prefix was accepted with only a note, although 27 CFR 16.22 requires capitals.

## Decision

- For alcohol content, net contents, age statement and vintage year, the local search returns the **text actually on the label**, so the numeric comparator sees the difference.
- A health warning whose text matches but whose `GOVERNMENT WARNING:` prefix is not in capitals is a **MISMATCH** (and therefore proposes rejection).
- Both behaviors are pinned by unit tests and by the synthetic `northvale-vodka-flawed` sample.

## Consequences

- Compliance-relevant differences are no longer hidden by fuzzy matching.
- Slightly more false mismatches when OCR misreads a digit; the specialist resolves them in review.

## Alternatives considered

- Keep fuzzy acceptance for all fields: silently approves wrong ABV/volume.
