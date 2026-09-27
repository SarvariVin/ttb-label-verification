package gov.ttb.labelverification.ai.compare;

import static gov.ttb.labelverification.ai.compare.TextNormalizer.fuzzyMatch;
import static gov.ttb.labelverification.ai.compare.TextNormalizer.normalizeWhitespace;

import gov.ttb.labelverification.regulatory.HealthWarning;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds an expected value inside noisy OCR text. Used by the local pipeline,
 * which has no LLM to classify text into fields.
 * <p>
 * Strategy, in order:
 * <ol>
 *   <li>Exact case-insensitive substring, not inside a longer word or number</li>
 *   <li>Health warning: landmark prefix + all 6 key body phrases, prefix capitals checked</li>
 *   <li>Space- and punctuation-insensitive match (OCR drops dots, commas, hyphens, spaces)</li>
 *   <li>Sliding word window with bigram similarity</li>
 *   <li>Scattered word matching (every significant word present somewhere)</li>
 * </ol>
 * A near-identical window (≥ 0.9) means the difference is OCR noise, so the
 * expected value is returned verbatim — except for numeric fields, where the
 * OCR text is returned so the comparator can catch a changed digit. Weaker
 * hits return the label's own text for the comparator to judge.
 */
public final class OcrTextSearch {

    public static final double MIN_SIMILARITY = 0.6;
    public static final double HIGH_CONFIDENCE = 0.75;
    /** Sliding-window similarity at which a difference is treated as OCR noise. */
    public static final double VERBATIM_SIMILARITY = 0.9;
    /** Letters in a warning prefix read as lower case that are still OCR noise (l for I). */
    private static final int MAX_LOWERCASE_IN_CAPS_PREFIX = 2;

    private static final List<String> LANDMARK_PREFIXES = List.of(HealthWarning.PREFIX.toLowerCase(Locale.ROOT));

    private static final List<String> HEALTH_WARNING_BODY_PHRASES = List.of(
            "surgeon general", "pregnancy", "birth defects", "drive a car", "operate machinery", "health problems");

    private OcrTextSearch() {
    }

    public static String find(String ocrText, String expectedValue) {
        return find(ocrText, expectedValue, false);
    }

    /**
     * @param preserveOcrValue true for values whose small differences are meaningful
     *                         (alcohol content, net contents, age, vintage). Fuzzy hits then
     *                         return the text actually on the label instead of the expected
     *                         value, so "40%" on the label is never reported as "42%".
     */
    public static String find(String ocrText, String expectedValue, boolean preserveOcrValue) {
        String normalizedOcr = normalizeWhitespace(ocrText);
        String normalizedExpected = normalizeWhitespace(expectedValue);
        if (normalizedOcr.isEmpty() || normalizedExpected.isEmpty()) {
            return null;
        }
        String lowerOcr = normalizedOcr.toLowerCase(Locale.ROOT);
        String lowerExpected = normalizedExpected.toLowerCase(Locale.ROOT);

        // Stage 1: exact substring, not inside a longer word or number ("5%" must not match "4.5%")
        int idx = indexAtBoundary(lowerOcr, lowerExpected);
        if (idx >= 0) {
            return normalizedOcr.substring(idx, idx + normalizedExpected.length());
        }

        // Stage 2: the health warning is never accepted on similarity alone — a label that drops a
        // clause still shares most of its words with the statutory text.
        for (String prefix : LANDMARK_PREFIXES) {
            if (lowerExpected.startsWith(prefix)) {
                return findHealthWarning(normalizedOcr, normalizedExpected);
            }
        }

        // Stage 3: same characters, ignoring spaces and punctuation OCR drops or adds ("1L" for "1 L")
        String span = flexibleSpan(normalizedOcr, normalizedExpected);
        if (span != null) {
            return preserveOcrValue ? span : expectedValue;
        }

        // Stage 4: sliding window
        String[] ocrWords = normalizedOcr.split("\\s+");
        int expectedWordCount = normalizedExpected.split("\\s+").length;
        double bestScore = 0;
        String bestMatch = null;
        int bestStart = 0;
        int bestSize = 0;
        int minWords = Math.max(1, expectedWordCount - 2);
        int maxWords = Math.min(ocrWords.length, expectedWordCount + 3);
        for (int ws = minWords; ws <= maxWords; ws++) {
            for (int i = 0; i <= ocrWords.length - ws; i++) {
                String candidate = String.join(" ", Arrays.copyOfRange(ocrWords, i, i + ws));
                double sim = fuzzyMatch(candidate, normalizedExpected).similarity();
                if (sim > bestScore) {
                    bestScore = sim;
                    bestMatch = candidate;
                    bestStart = i;
                    bestSize = ws;
                }
            }
        }
        // Only a near-identical window is OCR noise; below that, return what the label says and
        // let the comparator judge ("…Distillery, Portland, Maine" is not "…Distillery, Austin, Texas").
        if (bestScore >= VERBATIM_SIMILARITY) {
            return preserveOcrValue ? bestMatch : expectedValue;
        }
        if (bestScore >= HIGH_CONFIDENCE) {
            return widen(ocrWords, bestStart, bestSize, expectedWordCount, normalizedExpected);
        }

        // Stage 5: scattered words (decorative labels put each word on its own line)
        List<String> expectedWords = Arrays.stream(normalizedExpected.split("\\s+"))
                .filter(w -> w.length() >= 3).toList();
        if (expectedWords.size() >= 2 && !preserveOcrValue) {
            String[] ocrLower = lowerOcr.split("\\s+");
            boolean allFound = expectedWords.stream()
                    .map(w -> w.toLowerCase(Locale.ROOT))
                    .allMatch(ew -> Arrays.stream(ocrLower).anyMatch(ow -> ow.equals(ew)
                            || (Math.abs(ow.length() - ew.length()) <= 3
                            && fuzzyMatch(ow, ew).similarity() >= HIGH_CONFIDENCE)));
            if (allFound) {
                return expectedValue;
            }
        }

        return bestScore >= MIN_SIMILARITY ? bestMatch : null;
    }

    /**
     * Health warning search. Returns the statutory text only when the landmark prefix and every key
     * body phrase are legible. A title-case prefix is reported as such so the comparator can reject
     * it. When some but not all body phrases are legible, returns the text actually on the label
     * (a dropped clause is a mismatch, not OCR noise); when the body is illegible, returns null.
     */
    private static String findHealthWarning(String normalizedOcr, String normalizedExpected) {
        String prefix = HealthWarning.PREFIX;
        String[] prefixWords = clean(prefix.toLowerCase(Locale.ROOT)).split("\\s+");
        String target = String.join(" ", prefixWords);
        String[] words = normalizedOcr.split("\\s+");

        int start = -1;
        for (int i = 0; i <= words.length - prefixWords.length; i++) {
            String candidate = clean(String.join(" ", Arrays.copyOfRange(words, i, i + prefixWords.length))
                    .toLowerCase(Locale.ROOT));
            if (fuzzyMatch(candidate, target).similarity() >= HIGH_CONFIDENCE) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return null;
        }

        int end = Math.min(words.length, start + normalizedExpected.split("\\s+").length + 10);
        String onLabel = String.join(" ", Arrays.copyOfRange(words, start, end));
        int phrases = healthWarningPhrasesFound(onLabel);
        if (phrases == HEALTH_WARNING_BODY_PHRASES.size()) {
            String labelPrefix = String.join(" ", Arrays.copyOfRange(words, start, start + prefixWords.length));
            return prefixInCapitals(labelPrefix)
                    ? normalizedExpected
                    : titleCase(prefix) + normalizedExpected.substring(prefix.length());
        }
        return phrases >= 2 ? onLabel : null;
    }

    /** Number of the six key health-warning body phrases legible in the text (OCR-tolerant). */
    static int healthWarningPhrasesFound(String text) {
        String ocrClean = clean(text.toLowerCase(Locale.ROOT));
        String[] ocrW = ocrClean.split("\\s+");
        int phrasesFound = 0;
        for (String phrase : HEALTH_WARNING_BODY_PHRASES) {
            if (ocrClean.contains(phrase)) {
                phrasesFound++;
                continue;
            }
            // Every phrase is now required, so tolerate one misread letter per word ("Surgcon").
            boolean allWords = Arrays.stream(phrase.split("\\s+")).allMatch(pw -> Arrays.stream(ocrW)
                    .anyMatch(ow -> Math.abs(ow.length() - pw.length()) <= 2
                            && fuzzyMatch(ow, pw).similarity() >= MIN_SIMILARITY));
            if (allWords) {
                phrasesFound++;
            }
        }
        return phrasesFound;
    }

    static boolean allHealthWarningPhrasesPresent(String text) {
        return healthWarningPhrasesFound(text) == HEALTH_WARNING_BODY_PHRASES.size();
    }

    /** True when the warning's opening words are in capitals, allowing an OCR slip or two (l for I). */
    static boolean prefixInCapitals(String text) {
        String head = text.substring(0, Math.min(text.length(), HealthWarning.PREFIX.length()));
        return head.chars().filter(Character::isLowerCase).count() <= MAX_LOWERCASE_IN_CAPS_PREFIX;
    }

    /**
     * A window shorter than the declared value would let the comparator accept a truncated read
     * ("Silver Heron Distillery," inside "…Distillery, Austin, Texas"). Widen it to the declared
     * word count, keeping the original window, so the words that differ are compared too.
     */
    private static String widen(String[] words, int start, int size, int target, String expected) {
        if (size >= target || words.length < target) {
            return String.join(" ", Arrays.copyOfRange(words, start, start + size));
        }
        String best = null;
        double bestScore = -1;
        int from = Math.max(0, start + size - target);
        int to = Math.min(start, words.length - target);
        for (int i = from; i <= to; i++) {
            String candidate = String.join(" ", Arrays.copyOfRange(words, i, i + target));
            double sim = fuzzyMatch(candidate, expected).similarity();
            if (sim > bestScore) {
                bestScore = sim;
                best = candidate;
            }
        }
        return best;
    }

    private static String titleCase(String s) {
        StringBuilder b = new StringBuilder();
        for (String w : s.toLowerCase(Locale.ROOT).split(" ")) {
            b.append(b.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }

    private static String clean(String s) {
        return s.replaceAll("[.:,]", "");
    }

    /** Index of {@code needle} in {@code haystack} where it is not part of a longer word or number. */
    private static int indexAtBoundary(String haystack, String needle) {
        for (int idx = haystack.indexOf(needle); idx >= 0; idx = haystack.indexOf(needle, idx + 1)) {
            if (boundaryBefore(haystack, idx, needle.charAt(0))
                    && boundaryAfter(haystack, idx + needle.length(), needle.charAt(needle.length() - 1))) {
                return idx;
            }
        }
        return -1;
    }

    private static boolean boundaryBefore(String s, int idx, char first) {
        if (idx == 0 || !Character.isLetterOrDigit(first)) {
            return true;
        }
        char prev = s.charAt(idx - 1);
        if (Character.isLetterOrDigit(prev)) {
            return false;
        }
        // "5%" inside "4.5%" or "1,750 mL"
        return !(Character.isDigit(first) && (prev == '.' || prev == ',') && idx >= 2
                && Character.isDigit(s.charAt(idx - 2)));
    }

    private static boolean boundaryAfter(String s, int end, char last) {
        if (end >= s.length() || !Character.isLetterOrDigit(last)) {
            return true;
        }
        char next = s.charAt(end);
        if (Character.isLetterOrDigit(next)) {
            return false;
        }
        // "12" inside "12.5"
        return !(Character.isDigit(last) && (next == '.' || next == ',') && end + 1 < s.length()
                && Character.isDigit(s.charAt(end + 1)));
    }

    /**
     * Finds the expected letters and digits in order, allowing spaces and punctuation to differ
     * ("STONES THROW" for "Stone's Throw", "1L" for "1 L"). Returns the span as it reads on the label.
     */
    private static String flexibleSpan(String ocr, String expected) {
        StringBuilder regex = new StringBuilder();
        for (char c : expected.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                if (!regex.isEmpty()) {
                    regex.append("[\\s.,'\\-/]*");
                }
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        if (regex.isEmpty()) {
            return null;
        }
        Matcher m = Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(ocr);
        String lettersOnly = expected.replaceAll("[^\\p{L}\\p{N}]", "");
        while (m.find()) {
            if (boundaryBefore(ocr, m.start(), lettersOnly.charAt(0))
                    && boundaryAfter(ocr, m.end(), lettersOnly.charAt(lettersOnly.length() - 1))) {
                return m.group();
            }
        }
        return null;
    }
}
