package gov.ttb.labelverification.ai.compare;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** String normalization and similarity helpers shared by comparison and OCR search. */
public final class TextNormalizer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern PROOF = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*proof", Pattern.CASE_INSENSITIVE);
    private static final Pattern PERCENT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%");
    private static final Pattern QUANTITY = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEARS = Pattern.compile("(\\d+)\\s*(?:years?|yr)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AGED = Pattern.compile("aged\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    /** Insertion order matters: partial matching walks this map in order. */
    private static final Map<String, Double> UNIT_TO_ML = new LinkedHashMap<>();

    static {
        for (String u : new String[]{"ml", "milliliter", "milliliters", "millilitre", "millilitres"}) {
            UNIT_TO_ML.put(u, 1.0);
        }
        for (String u : new String[]{"cl", "centiliter", "centiliters", "centilitre", "centilitres"}) {
            UNIT_TO_ML.put(u, 10.0);
        }
        for (String u : new String[]{"l", "liter", "liters", "litre", "litres"}) {
            UNIT_TO_ML.put(u, 1000.0);
        }
        for (String u : new String[]{"oz", "fl oz", "fl. oz", "fl. oz.", "fluid ounce", "fluid ounces"}) {
            UNIT_TO_ML.put(u, 29.5735);
        }
        for (String u : new String[]{"pt", "pint", "pints"}) {
            UNIT_TO_ML.put(u, 473.176);
        }
        for (String u : new String[]{"qt", "quart", "quarts"}) {
            UNIT_TO_ML.put(u, 946.353);
        }
        for (String u : new String[]{"gal", "gallon", "gallons"}) {
            UNIT_TO_ML.put(u, 3785.41);
        }
    }

    private TextNormalizer() {
    }

    /** Collapses runs of whitespace into single spaces and trims. */
    public static String normalizeWhitespace(String text) {
        return text == null ? "" : WHITESPACE.matcher(text).replaceAll(" ").trim();
    }

    public record Similarity(boolean match, double similarity) {
    }

    /**
     * Dice coefficient over character bigrams (case-insensitive). A match is ≥ 0.8.
     */
    public static Similarity fuzzyMatch(String a, String b) {
        String normA = normalizeWhitespace(a);
        String normB = normalizeWhitespace(b);
        if (normA.equalsIgnoreCase(normB)) {
            return new Similarity(true, 1);
        }
        if (normA.length() < 2 || normB.length() < 2) {
            return new Similarity(false, 0);
        }
        Set<String> bigramsA = bigrams(normA);
        Set<String> bigramsB = bigrams(normB);
        int intersection = 0;
        for (String bg : bigramsA) {
            if (bigramsB.contains(bg)) {
                intersection++;
            }
        }
        double similarity = (2.0 * intersection) / (bigramsA.size() + bigramsB.size());
        return new Similarity(similarity >= 0.8, similarity);
    }

    private static Set<String> bigrams(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        Set<String> result = new HashSet<>();
        for (int i = 0; i < lower.length() - 1; i++) {
            result.add(lower.substring(i, i + 2));
        }
        return result;
    }

    /**
     * Numeric ABV from "45% Alc./Vol.", "12.5% ABV", "90 Proof" (→ 45). Null if unparseable.
     */
    public static Double normalizeAlcoholContent(String value) {
        String cleaned = normalizeWhitespace(value);
        Matcher proof = PROOF.matcher(cleaned);
        if (proof.find()) {
            return Double.parseDouble(proof.group(1)) / 2;
        }
        Matcher pct = PERCENT.matcher(cleaned);
        if (pct.find()) {
            return Double.parseDouble(pct.group(1));
        }
        return null;
    }

    /**
     * Net contents in mL from "750 mL", "75cL", "1.5 L", "25.4 FL OZ". Null if unparseable.
     */
    public static Double normalizeNetContents(String value) {
        String cleaned = normalizeWhitespace(value);
        Matcher m = QUANTITY.matcher(cleaned);
        if (!m.find()) {
            return null;
        }
        double numeric = Double.parseDouble(m.group(1));
        String unit = m.group(2).toLowerCase(Locale.ROOT).replaceAll("\\.$", "").trim();

        Double multiplier = UNIT_TO_ML.get(unit);
        if (multiplier == null) {
            for (Map.Entry<String, Double> e : UNIT_TO_ML.entrySet()) {
                if (unit.contains(e.getKey())) {
                    multiplier = e.getValue();
                    break;
                }
            }
        }
        if (multiplier == null) {
            return null;
        }
        return Math.round(numeric * multiplier * 100) / 100.0;
    }

    /** Years from "12 Years Old", "Aged 8". Null if unparseable. */
    public static Integer normalizeAgeStatement(String value) {
        String cleaned = normalizeWhitespace(value);
        Matcher years = YEARS.matcher(cleaned);
        if (years.find()) {
            return Integer.parseInt(years.group(1));
        }
        Matcher aged = AGED.matcher(cleaned);
        if (aged.find()) {
            return Integer.parseInt(aged.group(1));
        }
        return null;
    }
}
