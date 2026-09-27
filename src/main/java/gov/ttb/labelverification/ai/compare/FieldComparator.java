package gov.ttb.labelverification.ai.compare;

import static gov.ttb.labelverification.ai.compare.TextNormalizer.fuzzyMatch;
import static gov.ttb.labelverification.ai.compare.TextNormalizer.normalizeWhitespace;

import gov.ttb.labelverification.domain.ItemStatus;
import gov.ttb.labelverification.regulatory.FieldName;
import gov.ttb.labelverification.regulatory.HealthWarning;
import gov.ttb.labelverification.regulatory.MatchStrategy;
import gov.ttb.labelverification.regulatory.QualifyingPhrases;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Field comparison engine. Compares an expected (application) value against an
 * extracted (OCR/AI) value using the field's {@link MatchStrategy}.
 * <p>
 * Pure and stateless — safe to call from anywhere, heavily unit-tested.
 */
public final class FieldComparator {

    private FieldComparator() {
    }

    public static ComparisonResult compare(FieldName field, String expected, String extracted) {
        return compare(field, expected, extracted, field.strategy());
    }

    public static ComparisonResult compare(FieldName field, String expected, String extracted,
                                           MatchStrategy strategy) {
        String name = field.key();
        if (extracted == null || extracted.isBlank()) {
            return new ComparisonResult(ItemStatus.NOT_FOUND, 0,
                    "Field \"" + name + "\" was not found on the label.");
        }

        ComparisonResult result = switch (strategy) {
            case EXACT -> compareExact(field, expected, extracted);
            case FUZZY -> compareFuzzy(name, expected, extracted);
            case NORMALIZED -> compareNormalized(field, expected, extracted);
            case CONTAINS -> compareContains(name, expected, extracted);
            case ENUM -> compareEnum(field, expected, extracted);
        };

        // A confident "match" should never drag the overall average below the
        // Ready-to-Approve threshold just because the strategy's raw score is low.
        if (result.status() == ItemStatus.MATCH && result.confidence() < 95) {
            return result.withConfidence(95);
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Strategies
    // -----------------------------------------------------------------------

    private static ComparisonResult compareExact(FieldName field, String expected, String extracted) {
        String name = field.key();
        String normExpected = normalizeWhitespace(expected);
        String normExtracted = normalizeWhitespace(extracted);

        if (normExpected.equals(normExtracted)) {
            return match(100, name + " matches exactly after whitespace normalization.");
        }

        if (field == FieldName.VINTAGE_YEAR) {
            String expYear = normExpected.replaceAll("\\D", "");
            String extYear = normExtracted.replaceAll("\\D", "");
            if (expYear.equals(extYear)) {
                return match(95, name + " year values match: " + expYear + ".");
            }
        }

        if (field == FieldName.HEALTH_WARNING) {
            if (normExpected.equalsIgnoreCase(normExtracted)) {
                // 27 CFR 16.22: "GOVERNMENT WARNING" must appear in capital letters.
                boolean prefixCaps = normExtracted.startsWith(HealthWarning.PREFIX);
                if (!prefixCaps && normExpected.startsWith(HealthWarning.PREFIX)) {
                    return mismatch(90, name + " text is correct but the \"GOVERNMENT WARNING:\" prefix is not "
                            + "in capital letters (27 CFR 16.22).");
                }
                return match(85, name + " matches case-insensitively; the required prefix is in capitals.");
            }
            // Bigram similarity alone is not enough: a warning missing clause (2) still scores ~0.8+.
            double similarity = fuzzyMatch(normExpected, normExtracted).similarity();
            if (similarity >= 0.9 && OcrTextSearch.allHealthWarningPhrasesPresent(normExtracted)) {
                if (!OcrTextSearch.prefixInCapitals(normExtracted)) {
                    return mismatch(90, name + " text is correct but the \"GOVERNMENT WARNING:\" prefix is not "
                            + "in capital letters (27 CFR 16.22).");
                }
                return match((int) Math.round(similarity * 80), name + " is very similar ("
                        + Math.round(similarity * 100) + "%). Minor OCR discrepancies detected.");
            }
        }

        return new ComparisonResult(ItemStatus.MISMATCH, 90, name + " does not match. Expected: \""
                + truncate(normExpected, 100) + "...\" Found: \"" + truncate(normExtracted, 100) + "...\"");
    }

    private static ComparisonResult compareFuzzy(String name, String expected, String extracted) {
        TextNormalizer.Similarity sim = fuzzyMatch(expected, extracted);
        if (sim.match()) {
            long pct = Math.round(sim.similarity() * 100);
            return match((int) pct, name + " matches with " + pct + "% similarity.");
        }

        String normExpected = normalizeWhitespace(expected).toLowerCase(Locale.ROOT);
        String normExtracted = normalizeWhitespace(extracted).toLowerCase(Locale.ROOT);

        // Containment handles partial OCR reads.
        if (normExtracted.contains(normExpected) || normExpected.contains(normExtracted)) {
            double containment = (double) Math.min(normExpected.length(), normExtracted.length())
                    / Math.max(normExpected.length(), normExtracted.length());
            return match((int) Math.round(containment * 85), name + " partially matches (containment). Similarity: "
                    + Math.round(containment * 100) + "%.");
        }

        return new ComparisonResult(ItemStatus.MISMATCH, (int) Math.round((1 - sim.similarity()) * 90),
                name + " does not match. Similarity: " + Math.round(sim.similarity() * 100)
                        + "%. Expected: \"" + expected + "\" Found: \"" + extracted + "\"");
    }

    private static ComparisonResult compareNormalized(FieldName field, String expected, String extracted) {
        String name = field.key();
        switch (field) {
            case ALCOHOL_CONTENT -> {
                Double exp = TextNormalizer.normalizeAlcoholContent(expected);
                Double ext = TextNormalizer.normalizeAlcoholContent(extracted);
                if (exp == null || ext == null) {
                    return compareFuzzy(name, expected, extracted);
                }
                // Under half a point is rounding ("40%" vs "40.4%"); half a point or more is a different statement
                if (Math.abs(exp - ext) < 0.5) {
                    return match(exp.equals(ext) ? 100 : 90, "Alcohol content matches: expected "
                            + fmt(exp) + "%, found " + fmt(ext) + "%.");
                }
                return mismatch(95, "Alcohol content mismatch: expected " + fmt(exp) + "%, found " + fmt(ext) + "%.");
            }
            case NET_CONTENTS -> {
                Double exp = TextNormalizer.normalizeNetContents(expected);
                Double ext = TextNormalizer.normalizeNetContents(extracted);
                if (exp == null || ext == null) {
                    return compareFuzzy(name, expected, extracted);
                }
                // 1% tolerance for unit conversion rounding
                if (Math.abs(exp - ext) <= exp * 0.01) {
                    return match(exp.equals(ext) ? 100 : 90, "Net contents matches: expected "
                            + fmt(exp) + "mL, found " + fmt(ext) + "mL.");
                }
                return mismatch(95, "Net contents mismatch: expected " + fmt(exp) + "mL, found " + fmt(ext) + "mL.");
            }
            case AGE_STATEMENT -> {
                Integer exp = TextNormalizer.normalizeAgeStatement(expected);
                Integer ext = TextNormalizer.normalizeAgeStatement(extracted);
                if (exp == null || ext == null) {
                    return compareFuzzy(name, expected, extracted);
                }
                if (exp.equals(ext)) {
                    return match(100, "Age statement matches: " + exp + " years.");
                }
                return mismatch(95, "Age statement mismatch: expected " + exp + " years, found " + ext + " years.");
            }
            default -> {
                return compareFuzzy(name, expected, extracted);
            }
        }
    }

    private static ComparisonResult compareContains(String name, String expected, String extracted) {
        String normExpected = normalizeWhitespace(expected).toLowerCase(Locale.ROOT);
        String normExtracted = normalizeWhitespace(extracted).toLowerCase(Locale.ROOT);

        if (normExtracted.contains(normExpected) || normExpected.contains(normExtracted)) {
            return match(90, name + " found within extracted text.");
        }

        String[] expectedWords = normExpected.split(" ");
        List<String> matching = new ArrayList<>();
        for (String w : expectedWords) {
            if (normExtracted.contains(w)) {
                matching.add(w);
            }
        }
        double overlap = (double) matching.size() / expectedWords.length;
        if (overlap >= 0.5) {
            return match((int) Math.round(overlap * 80), name + " partially matches ("
                    + String.join(", ", matching) + " found in extracted text).");
        }
        return mismatch(85, name + " not found in extracted text. Expected: \"" + expected
                + "\" Found: \"" + extracted + "\"");
    }

    private static ComparisonResult compareEnum(FieldName field, String expected, String extracted) {
        if (field == FieldName.QUALIFYING_PHRASE) {
            String normExpected = normalizeWhitespace(expected).toLowerCase(Locale.ROOT);
            String normExtracted = normalizeWhitespace(extracted).toLowerCase(Locale.ROOT);
            Optional<String> expectedPhrase = findPhrase(normExpected);
            Optional<String> extractedPhrase = findPhrase(normExtracted);
            if (expectedPhrase.isPresent() && extractedPhrase.isPresent()) {
                if (expectedPhrase.get().equals(extractedPhrase.get())) {
                    return match(95, "Qualifying phrase matches: \"" + expectedPhrase.get() + "\".");
                }
                return mismatch(90, "Qualifying phrase mismatch: expected \"" + expectedPhrase.get()
                        + "\", found \"" + extractedPhrase.get() + "\".");
            }
        }
        return compareFuzzy(field.key(), expected, extracted);
    }

    private static Optional<String> findPhrase(String text) {
        return QualifyingPhrases.LOWERCASE.stream()
                .filter(p -> text.contains(p) || p.contains(text))
                .findFirst();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static ComparisonResult match(int confidence, String reasoning) {
        return new ComparisonResult(ItemStatus.MATCH, confidence, reasoning);
    }

    private static ComparisonResult mismatch(int confidence, String reasoning) {
        return new ComparisonResult(ItemStatus.MISMATCH, confidence, reasoning);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Formats like JavaScript: 45.0 → "45", 12.5 → "12.5". */
    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
