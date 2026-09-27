package gov.ttb.labelverification.labels;

import static gov.ttb.labelverification.regulatory.RegulatoryConstants.CONDITIONAL_DEADLINE_DAYS;
import static gov.ttb.labelverification.regulatory.RegulatoryConstants.CORRECTION_DEADLINE_DAYS;
import static gov.ttb.labelverification.regulatory.RegulatoryConstants.MINOR_DISCREPANCY_FIELDS;
import static gov.ttb.labelverification.regulatory.RegulatoryConstants.REJECTION_FIELDS;

import gov.ttb.labelverification.domain.ItemStatus;
import gov.ttb.labelverification.domain.LabelStatus;
import gov.ttb.labelverification.regulatory.BeverageType;
import gov.ttb.labelverification.regulatory.FieldName;
import java.util.Collection;

/**
 * Derives the overall label verdict from field-level results.
 * <pre>
 *   illegal container size                      → REJECTED
 *   health warning missing / mismatched         → REJECTED
 *   mandatory field missing / mismatched        → NEEDS_CORRECTION (30 days)
 *   minor or optional field mismatched          → CONDITIONALLY_APPROVED (7 days)
 *   everything matches                          → APPROVED
 * </pre>
 */
public final class StatusDeterminer {

    public record FieldOutcome(FieldName fieldName, ItemStatus status) {
    }

    private StatusDeterminer() {
    }

    /**
     * @param containerSizeMl null to skip the standards-of-fill check (specialist reviews)
     */
    public static StatusDecision determine(Collection<FieldOutcome> items, BeverageType beverageType,
                                           Integer containerSizeMl) {
        if (containerSizeMl != null && !beverageType.isValidSize(containerSizeMl)) {
            return new StatusDecision(LabelStatus.REJECTED, null);
        }

        boolean rejection = false;
        boolean substantive = false;
        boolean minor = false;

        for (FieldOutcome item : items) {
            boolean mandatory = beverageType.isMandatory(item.fieldName());
            boolean rejectionField = REJECTION_FIELDS.contains(item.fieldName());
            boolean minorField = MINOR_DISCREPANCY_FIELDS.contains(item.fieldName());

            switch (item.status()) {
                case MATCH -> {
                    // nothing to do
                }
                case NOT_FOUND -> {
                    if (mandatory) {
                        if (rejectionField) {
                            rejection = true;
                        } else {
                            substantive = true;
                        }
                    }
                }
                case MISMATCH, NEEDS_CORRECTION -> {
                    if (rejectionField) {
                        rejection = true;
                    } else if (minorField) {
                        minor = true;
                    } else if (mandatory) {
                        substantive = true;
                    } else {
                        minor = true;
                    }
                }
            }
        }

        if (rejection) {
            return new StatusDecision(LabelStatus.REJECTED, null);
        }
        if (substantive) {
            return new StatusDecision(LabelStatus.NEEDS_CORRECTION, CORRECTION_DEADLINE_DAYS);
        }
        if (minor) {
            return new StatusDecision(LabelStatus.CONDITIONALLY_APPROVED, CONDITIONAL_DEADLINE_DAYS);
        }
        return new StatusDecision(LabelStatus.APPROVED, null);
    }
}
