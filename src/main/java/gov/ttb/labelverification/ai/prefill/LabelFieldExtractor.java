package gov.ttb.labelverification.ai.prefill;

import gov.ttb.labelverification.ai.BeverageDetector;
import gov.ttb.labelverification.ai.compare.TextNormalizer;
import gov.ttb.labelverification.ai.ocr.OcrLine;
import gov.ttb.labelverification.regulatory.BeverageType;
import gov.ttb.labelverification.regulatory.FieldName;
import gov.ttb.labelverification.regulatory.QualifyingPhrases;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based extraction of Form 5100.31 fields from OCR text lines, used to
 * pre-fill the submission form without any cloud service.
 * <p>
 * Pure and deterministic. Each line is claimed by at most one field:
 * <ol>
 *   <li>health warning block ("GOVERNMENT WARNING" … "health problems")</li>
 *   <li>alcohol content, net contents, age statement, country of origin (patterns)</li>
 *   <li>qualifying phrase; the rest of that line, or the next line, is the name and address</li>
 *   <li>class/type: the line with the highest share of class vocabulary</li>
 *   <li>brand: the tallest remaining line (largest type)</li>
 *   <li>fanciful name: an unclaimed line between the brand and the class/type</li>
 *   <li>wine: vintage year, appellation and sulfite declaration</li>
 * </ol>
 * Results are suggestions: the applicant must confirm them against their application.
 */
public final class LabelFieldExtractor {

    private static final Pattern WARNING_START = Pattern.compile("gover\\w*\\s+warn\\w*", Pattern.CASE_INSENSITIVE);
    private static final Pattern ABV = Pattern.compile(
            "(?:alc(?:ohol)?\\.?\\s*)?\\d{1,2}(?:\\.\\d{1,2})?\\s*%\\s*"
                    + "(?:alc(?:ohol)?\\.?\\s*(?:/|by)\\s*vol(?:ume)?\\.?|by\\s*vol(?:ume)?\\.?|abv)?"
                    + "(?:\\s*\\(\\s*\\d{2,3}(?:\\.\\d)?\\s*proof\\s*\\))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NET = Pattern.compile(
            "\\b\\d+(?:\\.\\d+)?\\s*(?:ml|cl|l|liters?|litres?|fl\\.?\\s*oz\\.?)(?![a-z])(?:\\s*\\(\\s*\\d+\\s*ml\\s*\\))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AGE = Pattern.compile(
            "aged\\s+[\\w-]+\\s+years?|\\b\\d{1,2}\\s+years?\\s+old", Pattern.CASE_INSENSITIVE);
    private static final Pattern COUNTRY = Pattern.compile(
            "(?:product of|produce of|made in|imported from|hecho en)\\s+[a-z .]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR = Pattern.compile("\\b(19[5-9]\\d|20\\d{2})\\b");
    private static final Pattern SULFITES = Pattern.compile("contains\\s+sulfites", Pattern.CASE_INSENSITIVE);

    /** Words that alone identify a class/type. */
    private static final List<String> CORE_CLASS = List.of(
            "whiskey", "whisky", "bourbon", "vodka", "gin", "rum", "tequila", "mezcal", "brandy", "cognac",
            "liqueur", "scotch", "wine", "chardonnay", "cabernet sauvignon", "sauvignon blanc", "pinot noir",
            "pinot grigio", "merlot", "riesling", "zinfandel", "syrah", "malbec", "rosé", "rose", "prosecco",
            "champagne", "lager", "ale", "ipa", "stout", "porter", "pilsner", "beer", "hard seltzer",
            "malt beverage", "cider");
    /** Words that qualify a class/type but never identify it alone. */
    private static final List<String> MODIFIERS = List.of(
            "kentucky", "tennessee", "straight", "single", "malt", "blended", "rye", "wheat", "corn", "london",
            "dry", "spiced", "blanco", "reposado", "anejo", "añejo", "red", "white", "sparkling", "pale",
            "india", "amber", "imperial", "table", "dessert", "grain");
    private static final List<String> VARIETALS = List.of(
            "chardonnay", "cabernet sauvignon", "sauvignon blanc", "pinot noir", "pinot grigio", "merlot",
            "riesling", "zinfandel", "syrah", "malbec");

    private LabelFieldExtractor() {
    }

    /** Extracted suggestions. Null members mean "not found". */
    public record Result(BeverageType beverageType, Integer containerSizeMl, Boolean sulfiteDeclaration,
                         Map<FieldName, String> fields) {
    }

    public static Result extract(List<OcrLine> input) {
        List<OcrLine> lines = new ArrayList<>(input);
        lines.sort(Comparator.comparingInt(OcrLine::top));
        Set<Integer> used = new HashSet<>();
        Map<FieldName, String> fields = new EnumMap<>(FieldName.class);

        // 1. Health warning block
        for (int i = 0; i < lines.size(); i++) {
            if (WARNING_START.matcher(lines.get(i).text()).find()) {
                StringBuilder sb = new StringBuilder();
                for (int j = i; j < lines.size(); j++) {
                    sb.append(j == i ? "" : " ").append(lines.get(j).text());
                    used.add(j);
                    if (lines.get(j).text().toLowerCase(Locale.ROOT).contains("health problems")) {
                        break;
                    }
                }
                String text = sb.toString();
                Matcher m = WARNING_START.matcher(text);
                fields.put(FieldName.HEALTH_WARNING, m.find() ? text.substring(m.start()).trim() : text.trim());
                break;
            }
        }

        // 2. Pattern fields (a line may hold several, e.g. "45% Alc./Vol. 750 mL")
        for (int i = 0; i < lines.size(); i++) {
            if (used.contains(i)) {
                continue;
            }
            String t = lines.get(i).text();
            boolean claimed = false;
            claimed |= putFirst(fields, FieldName.ALCOHOL_CONTENT, ABV, t, true);
            claimed |= putFirst(fields, FieldName.NET_CONTENTS, NET, t, false);
            claimed |= putFirst(fields, FieldName.AGE_STATEMENT, AGE, t, false);
            claimed |= putFirst(fields, FieldName.COUNTRY_OF_ORIGIN, COUNTRY, t, false);
            if (claimed) {
                used.add(i);
            }
        }

        // 3. Qualifying phrase + name and address
        for (int i = 0; i < lines.size() && !fields.containsKey(FieldName.QUALIFYING_PHRASE); i++) {
            if (used.contains(i)) {
                continue;
            }
            String t = lines.get(i).text();
            String normalized = t.toLowerCase(Locale.ROOT).replace("&", "and");
            String best = null;
            for (String phrase : QualifyingPhrases.LOWERCASE) {
                if (normalized.contains(phrase) && (best == null || phrase.length() > best.length())) {
                    best = phrase;
                }
            }
            if (best == null) {
                continue;
            }
            int start = normalized.indexOf(best);
            int end = mapEnd(t, start + best.length());
            fields.put(FieldName.QUALIFYING_PHRASE, t.substring(start, end).trim());
            used.add(i);
            String rest = clean(t.substring(end));
            if (!rest.isEmpty()) {
                fields.put(FieldName.NAME_AND_ADDRESS, rest);
            } else {
                int next = nextUnused(lines, used, i);
                if (next >= 0) {
                    fields.put(FieldName.NAME_AND_ADDRESS, clean(lines.get(next).text()));
                    used.add(next);
                }
            }
        }

        // 4. Class / type
        int classIdx = -1;
        double bestScore = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (used.contains(i)) {
                continue;
            }
            double score = classScore(lines.get(i).text());
            if (score > bestScore) {
                bestScore = score;
                classIdx = i;
            }
        }
        if (classIdx >= 0) {
            String cls = clean(lines.get(classIdx).text());
            fields.put(FieldName.CLASS_TYPE, cls);
            used.add(classIdx);
            String lower = cls.toLowerCase(Locale.ROOT);
            VARIETALS.stream().filter(lower::contains).findFirst()
                    .ifPresent(v -> fields.put(FieldName.GRAPE_VARIETAL, cls));
        }

        // 5. Wine details: vintage, appellation, sulfites (often one line: "Sonoma Coast · 2022 · Contains Sulfites")
        Boolean sulfites = null;
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).text();
            if (SULFITES.matcher(t).find()) {
                sulfites = Boolean.TRUE;
            }
            if (used.contains(i)) {
                continue;
            }
            Matcher year = YEAR.matcher(t);
            if (year.find() && !t.matches(".*\\d{3,}\\s*(ml|l)\\b.*")) {
                fields.putIfAbsent(FieldName.VINTAGE_YEAR, year.group(1));
                String rest = SULFITES.matcher(YEAR.matcher(t).replaceAll(" ")).replaceAll(" ");
                String appellation = clean(rest.replaceAll("[^\\p{L}\\s'-]", " "));
                if (appellation.length() >= 3) {
                    fields.putIfAbsent(FieldName.APPELLATION_OF_ORIGIN, appellation);
                }
                used.add(i);
            } else if (SULFITES.matcher(t).find()) {
                used.add(i);
            }
        }

        // 6. Brand: tallest unclaimed line with letters
        int brandIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (used.contains(i) || !lines.get(i).text().matches(".*\\p{L}{2,}.*")) {
                continue;
            }
            if (brandIdx < 0 || lines.get(i).height() > lines.get(brandIdx).height()) {
                brandIdx = i;
            }
        }
        if (brandIdx >= 0) {
            fields.put(FieldName.BRAND_NAME, clean(lines.get(brandIdx).text()));
            used.add(brandIdx);

            // 7. Fanciful name: first unclaimed line between brand and class/type
            int limit = classIdx > brandIdx ? classIdx : lines.size();
            for (int i = brandIdx + 1; i < limit; i++) {
                if (!used.contains(i) && lines.get(i).text().matches(".*\\p{L}{2,}.*")) {
                    if (classIdx > brandIdx) {
                        fields.put(FieldName.FANCIFUL_NAME, clean(lines.get(i).text()));
                        used.add(i);
                    }
                    break;
                }
            }
        }

        // 8. Beverage type and container size
        StringBuilder all = new StringBuilder();
        lines.forEach(l -> all.append(l.text()).append('\n'));
        BeverageType type = BeverageDetector.detect(all.toString());
        Integer size = null;
        String net = fields.get(FieldName.NET_CONTENTS);
        if (net != null) {
            Matcher paren = Pattern.compile("\\(\\s*(\\d+)\\s*ml\\s*\\)", Pattern.CASE_INSENSITIVE).matcher(net);
            Double ml = paren.find() ? Double.valueOf(paren.group(1)) : TextNormalizer.normalizeNetContents(net);
            if (ml != null) {
                size = (int) Math.round(ml);
                // "12 FL OZ" is 354.9 mL; the legal container size is 355 mL.
                if (net.toLowerCase(Locale.ROOT).contains("oz") && Math.abs(size - 355) <= 1) {
                    size = 355;
                }
            }
        }
        return new Result(type, size, sulfites, fields);
    }

    /** Share of the line's letters covered by class vocabulary; 0 unless a core class word is present. */
    static double classScore(String line) {
        String lower = " " + line.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L} ]", " ") + " ";
        boolean hasCore = CORE_CLASS.stream().anyMatch(k -> lower.contains(" " + k + " "));
        if (!hasCore || line.contains("%")) {
            return 0;
        }
        int letters = lower.replace(" ", "").length();
        int covered = 0;
        String remaining = lower;
        List<String> vocab = new ArrayList<>(CORE_CLASS);
        vocab.addAll(MODIFIERS);
        vocab.sort(Comparator.comparingInt(String::length).reversed());
        for (String k : vocab) {
            while (remaining.contains(" " + k + " ")) {
                covered += k.replace(" ", "").length();
                remaining = remaining.replaceFirst(" " + Pattern.quote(k) + " ", "  ");
            }
        }
        return letters == 0 ? 0 : (double) covered / letters;
    }

    private static boolean putFirst(Map<FieldName, String> fields, FieldName field, Pattern p, String text,
                                    boolean requireAlcoholWord) {
        if (fields.containsKey(field)) {
            return false;
        }
        Matcher m = p.matcher(text);
        while (m.find()) {
            String found = m.group().trim();
            if (requireAlcoholWord && !found.toLowerCase(Locale.ROOT).matches(".*(alc|vol|abv|proof).*")) {
                continue;
            }
            fields.put(field, found);
            return true;
        }
        return false;
    }

    /** Maps an index in the "&"→"and" normalized string back to the original line. */
    private static int mapEnd(String original, int normalizedEnd) {
        int n = 0;
        for (int i = 0; i < original.length(); i++) {
            n += original.charAt(i) == '&' ? 3 : 1;
            if (n >= normalizedEnd) {
                return i + 1;
            }
        }
        return original.length();
    }

    private static int nextUnused(List<OcrLine> lines, Set<Integer> used, int after) {
        for (int i = after + 1; i < lines.size(); i++) {
            if (!used.contains(i)) {
                return i;
            }
        }
        return -1;
    }

    private static String clean(String s) {
        return TextNormalizer.normalizeWhitespace(s).replaceAll("^[\\s,:;.|·—-]+|[\\s,:;|·—-]+$", "");
    }
}
