package gov.ttb.labelverification.ai.compare;

import gov.ttb.labelverification.domain.ItemStatus;

/**
 * Outcome of comparing one field.
 *
 * @param status     MATCH, MISMATCH or NOT_FOUND
 * @param confidence 0–100
 * @param reasoning  human-readable explanation shown to specialists
 */
public record ComparisonResult(ItemStatus status, int confidence, String reasoning) {

    ComparisonResult withConfidence(int newConfidence) {
        return new ComparisonResult(status, newConfidence, reasoning);
    }
}
