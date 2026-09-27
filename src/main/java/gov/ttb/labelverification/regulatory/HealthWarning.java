package gov.ttb.labelverification.regulatory;

import java.util.ArrayList;
import java.util.List;

/**
 * Mandatory health warning statement per 27 CFR Part 16.
 * <p>
 * "GOVERNMENT WARNING:" must be ALL CAPS and bold; the body must not be bold;
 * the whole statement must appear on one panel.
 */
public final class HealthWarning {

    public static final String PREFIX = "GOVERNMENT WARNING:";

    public static final String SECTION_1 =
            "(1) According to the Surgeon General, women should not drink alcoholic beverages "
                    + "during pregnancy because of the risk of birth defects.";

    public static final String SECTION_2 =
            "(2) Consumption of alcoholic beverages impairs your ability to drive a car or "
                    + "operate machinery, and may cause health problems.";

    public static final String FULL_TEXT = PREFIX + " " + SECTION_1 + " " + SECTION_2;

    private HealthWarning() {
    }

    public record Check(boolean valid, List<String> issues) {
    }

    /** Validates text against the required statement: prefix casing, both sections, section markers. */
    public static Check validate(String text) {
        List<String> issues = new ArrayList<>();
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return new Check(false, List.of("Health warning statement is empty"));
        }

        if (!trimmed.startsWith(PREFIX)) {
            if (trimmed.toLowerCase().startsWith(PREFIX.toLowerCase())) {
                issues.add("\"GOVERNMENT WARNING:\" prefix must be in ALL CAPS");
            } else {
                issues.add("Missing \"GOVERNMENT WARNING:\" prefix");
            }
        }

        String normalized = trimmed.replaceAll("\\s+", " ");
        if (!normalized.equals(FULL_TEXT)) {
            if (!normalized.contains(SECTION_1)) {
                issues.add("Missing or incorrect section (1) — Surgeon General pregnancy warning");
            }
            if (!normalized.contains(SECTION_2)) {
                issues.add("Missing or incorrect section (2) — impaired driving/machinery warning");
            }
            if (!normalized.contains("(1)")) {
                issues.add("Missing section number \"(1)\"");
            }
            if (!normalized.contains("(2)")) {
                issues.add("Missing section number \"(2)\"");
            }
            if (issues.isEmpty()) {
                issues.add("Health warning text does not match the required statement");
            }
        }
        return new Check(issues.isEmpty(), List.copyOf(issues));
    }
}
