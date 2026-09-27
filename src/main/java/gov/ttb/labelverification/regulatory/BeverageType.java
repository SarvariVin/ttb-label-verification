package gov.ttb.labelverification.regulatory;

import static gov.ttb.labelverification.regulatory.FieldName.*;

import java.util.List;
import java.util.Set;

/**
 * Beverage types regulated by TTB, with mandatory label fields and legal
 * container sizes (standards of fill, January 2025 final rule).
 */
public enum BeverageType {
    DISTILLED_SPIRITS(
            "Distilled Spirits",
            5,
            List.of(BRAND_NAME, CLASS_TYPE, ALCOHOL_CONTENT, NET_CONTENTS,
                    HEALTH_WARNING, NAME_AND_ADDRESS, QUALIFYING_PHRASE),
            List.of(FANCIFUL_NAME, COUNTRY_OF_ORIGIN, AGE_STATEMENT, STATE_OF_DISTILLATION, STANDARDS_OF_FILL),
            Set.of(50, 100, 187, 200, 250, 331, 350, 355, 375, 475, 500, 570, 700, 710, 720,
                    750, 900, 945, 1000, 1500, 1750, 1800, 2000, 3000, 3750)),

    WINE(
            "Wine",
            4,
            List.of(BRAND_NAME, CLASS_TYPE, ALCOHOL_CONTENT, NET_CONTENTS,
                    HEALTH_WARNING, NAME_AND_ADDRESS, QUALIFYING_PHRASE, SULFITE_DECLARATION),
            // grape_varietal is conditionally mandatory (27 CFR 4.23) — treated as optional here.
            List.of(FANCIFUL_NAME, COUNTRY_OF_ORIGIN, GRAPE_VARIETAL, APPELLATION_OF_ORIGIN,
                    VINTAGE_YEAR, STANDARDS_OF_FILL),
            // 27 CFR 4.72 — does NOT include 200 or 250 mL (spirits-only sizes).
            Set.of(50, 100, 180, 187, 300, 330, 360, 375, 473, 500, 550, 568, 600, 620, 700,
                    720, 750, 1000, 1500, 1800, 2250, 3000)),

    MALT_BEVERAGE(
            "Malt Beverages",
            7,
            List.of(BRAND_NAME, CLASS_TYPE, NET_CONTENTS, HEALTH_WARNING, NAME_AND_ADDRESS, QUALIFYING_PHRASE),
            List.of(FANCIFUL_NAME, ALCOHOL_CONTENT, COUNTRY_OF_ORIGIN, STANDARDS_OF_FILL),
            null);

    private final String label;
    private final int cfrPart;
    private final List<FieldName> mandatoryFields;
    private final List<FieldName> optionalFields;
    private final Set<Integer> validSizesMl;

    BeverageType(String label, int cfrPart, List<FieldName> mandatoryFields,
                 List<FieldName> optionalFields, Set<Integer> validSizesMl) {
        this.label = label;
        this.cfrPart = cfrPart;
        this.mandatoryFields = mandatoryFields;
        this.optionalFields = optionalFields;
        this.validSizesMl = validSizesMl;
    }

    public String label() {
        return label;
    }

    /** CFR Part governing this type (4 = Wine, 5 = Spirits, 7 = Malt). */
    public int cfrPart() {
        return cfrPart;
    }

    public List<FieldName> mandatoryFields() {
        return mandatoryFields;
    }

    public List<FieldName> optionalFields() {
        return optionalFields;
    }

    public boolean isMandatory(FieldName field) {
        return mandatoryFields.contains(field);
    }

    /** True when the type has no size restriction or the size is a legal standard of fill. */
    public boolean isValidSize(int sizeMl) {
        return validSizesMl == null || validSizesMl.contains(sizeMl);
    }

    /**
     * Minimum health warning type size in mm per 27 CFR 16.22:
     * ≤237 mL → 1 mm, ≤3000 mL → 2 mm, larger → 3 mm.
     */
    public static int healthWarningMinTypeSizeMm(int containerSizeMl) {
        if (containerSizeMl <= 237) {
            return 1;
        }
        if (containerSizeMl <= 3000) {
            return 2;
        }
        return 3;
    }
}
