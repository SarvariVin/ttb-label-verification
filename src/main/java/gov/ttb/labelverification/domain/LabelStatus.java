package gov.ttb.labelverification.domain;

import java.util.Set;

/** Lifecycle status of a label submission. See docs/architecture.md for the state machine. */
public enum LabelStatus {
    PENDING,
    PROCESSING,
    PENDING_REVIEW,
    APPROVED,
    CONDITIONALLY_APPROVED,
    NEEDS_CORRECTION,
    REJECTED;

    /** Statuses a specialist may submit a field-level review against. */
    public static final Set<LabelStatus> REVIEWABLE =
            Set.of(PENDING_REVIEW, NEEDS_CORRECTION, CONDITIONALLY_APPROVED, PROCESSING);

    /** Final decisions a specialist can set via an override. */
    public static final Set<LabelStatus> DECISIONS =
            Set.of(APPROVED, CONDITIONALLY_APPROVED, NEEDS_CORRECTION, REJECTED);

    public String displayName() {
        String s = name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
