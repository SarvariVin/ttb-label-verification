# ADR-0020: Near misses are compared, not assumed

- **Status:** Accepted (extends [ADR-0012](0012-numeric-fields-keep-ocr-text-warning-prefix-must-be-capitals.md))
- **Date:** 2026-09-24

## Context

34 synthetic labels were run through the deployed application. OCR read every one correctly, yet five verdicts were wrong. All five came from the local text search or the comparator:

- **Missing warning clause:** a health warning without clause (2) was accepted. Three of the six key body phrases, plus the bigram similarity of the remaining text, were enough for a match, and the full statutory text was reported as found.
- **Different city:** a label reading "Silver Heron Distillery, Portland, Maine" was reported as the declared "…Austin, Texas". A shorter window, "Silver Heron Distillery,", scored high and was either returned verbatim or accepted by the comparator's containment rule.
- **ABV at the boundary:** 6.0% declared against 5.5% on the label passed the ±0.5 tolerance.
- **Missing space:** `1L` on the label was not found for a declared `1 L`.
- **Number inside a number** (found while measuring): a declared `5%` was found inside `4.5%`.

The common cause: a strong fuzzy hit was treated as "the declared value is on the label". For a compliance tool, a false approval goes unnoticed, while a false rejection is corrected by a specialist.

## Decision

- **Health warning:** accepted only when the landmark prefix **and all six** key body phrases are legible. One misread letter per word is tolerated. If the prefix is title case, it is reported as such. When only some phrases are legible, the search returns the text actually on the label. The comparator's OCR-noise path also needs all six phrases and re-checks the prefix capitals.
- **Verbatim threshold raised** from 0.75 to 0.9. Between 0.75 and 0.9, the search returns the label's own text, widened to the declared word count, and the comparator decides.
- **Whole-value matching:** substring hits must sit at word or number boundaries.
- **Space- and punctuation-insensitive stage:** matches the same letters and digits in order (`1L` = `1 L`, `STONES THROW` = `Stone's Throw`).
- **Alcohol content:** a difference under 0.5 points is rounding; 0.5 or more is a mismatch.
- Each case is pinned by a unit test in `OcrTextSearchTest` / `FieldComparatorTest`.

## Consequences

- After the change, the same 34 labels gave 33 expected verdicts (29 before). The remaining case follows the documented rule that a declared optional field missing from the label is ignored.
- Blurred real photos whose warning is partly illegible are proposed *Rejected* more often and need a specialist. This is accepted as the safer failure mode.
- Mismatch reasoning now shows what the label really says, which makes reviews faster.

## Alternatives considered

- **Tune the similarity thresholds only:** bigram-set similarity grows with text length, so a warning missing a whole clause still scores about 0.8. No single threshold separates it from OCR noise.
- **Rely on the cloud pipeline:** it reports what the label says, but it is optional, and the local pipeline must be correct on its own.
