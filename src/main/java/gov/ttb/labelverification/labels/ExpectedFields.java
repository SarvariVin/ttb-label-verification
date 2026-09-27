package gov.ttb.labelverification.labels;

import gov.ttb.labelverification.domain.ApplicationData;
import gov.ttb.labelverification.regulatory.BeverageType;
import gov.ttb.labelverification.regulatory.FieldName;
import gov.ttb.labelverification.regulatory.HealthWarning;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the field → expected value map that a label is verified against. */
public final class ExpectedFields {

    private ExpectedFields() {
    }

    /**
     * Every field the applicant provided, plus the health warning.
     * <p>
     * The health warning is always the statutory text of 27 CFR Part 16, whatever the
     * applicant declared: the wording is fixed by law, and verifying a label against a
     * declared (possibly non-compliant, possibly pre-filled from the label itself) text
     * would let a defective warning pass.
     */
    public static Map<FieldName, String> from(ApplicationData data, BeverageType beverageType) {
        Map<FieldName, String> fields = new LinkedHashMap<>();
        for (FieldName field : FieldName.values()) {
            if (field == FieldName.HEALTH_WARNING) {
                continue;
            }
            String value = data.valueOf(field);
            if (value != null && !value.isBlank()) {
                fields.put(field, value.trim());
            }
        }
        fields.put(FieldName.HEALTH_WARNING, HealthWarning.FULL_TEXT);
        return fields;
    }
}
