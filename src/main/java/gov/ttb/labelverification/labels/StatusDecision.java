package gov.ttb.labelverification.labels;

import gov.ttb.labelverification.domain.LabelStatus;

/**
 * @param deadlineDays correction window in days, or null when no deadline applies
 */
public record StatusDecision(LabelStatus status, Integer deadlineDays) {
}
