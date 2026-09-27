package gov.ttb.labelverification.regulatory;

import java.util.Arrays;
import java.util.Optional;

/**
 * TTB label fields (Form 5100.31 items) that the pipeline extracts and compares.
 * Each field carries its comparison strategy and a human-readable name.
 */
public enum FieldName {
    BRAND_NAME("Brand Name", MatchStrategy.FUZZY),
    FANCIFUL_NAME("Fanciful Name", MatchStrategy.FUZZY),
    CLASS_TYPE("Class/Type Designation", MatchStrategy.FUZZY),
    ALCOHOL_CONTENT("Alcohol Content", MatchStrategy.NORMALIZED),
    NET_CONTENTS("Net Contents", MatchStrategy.NORMALIZED),
    HEALTH_WARNING("Health Warning Statement", MatchStrategy.EXACT),
    NAME_AND_ADDRESS("Name and Address", MatchStrategy.FUZZY),
    QUALIFYING_PHRASE("Qualifying Phrase", MatchStrategy.ENUM),
    COUNTRY_OF_ORIGIN("Country of Origin", MatchStrategy.CONTAINS),
    GRAPE_VARIETAL("Grape Varietal", MatchStrategy.FUZZY),
    APPELLATION_OF_ORIGIN("Appellation of Origin", MatchStrategy.FUZZY),
    VINTAGE_YEAR("Vintage Year", MatchStrategy.EXACT),
    SULFITE_DECLARATION("Sulfite Declaration", MatchStrategy.FUZZY),
    AGE_STATEMENT("Age Statement", MatchStrategy.NORMALIZED),
    STATE_OF_DISTILLATION("State of Distillation", MatchStrategy.FUZZY),
    STANDARDS_OF_FILL("Standards of Fill", MatchStrategy.EXACT);

    private final String displayName;
    private final MatchStrategy strategy;

    FieldName(String displayName, MatchStrategy strategy) {
        this.displayName = displayName;
        this.strategy = strategy;
    }

    public String displayName() {
        return displayName;
    }

    public MatchStrategy strategy() {
        return strategy;
    }

    /** snake_case key used in prompts, CSV headers and JSON payloads. */
    public String key() {
        return name().toLowerCase();
    }

    /** camelCase property name used by the submission form and API, e.g. "brandName". */
    public String formProperty() {
        String[] parts = key().split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return sb.toString();
    }

    public static Optional<FieldName> fromKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String normalized = key.trim().toUpperCase();
        return Arrays.stream(values()).filter(f -> f.name().equals(normalized)).findFirst();
    }
}
