package gov.ttb.labelverification.labels;

import gov.ttb.labelverification.domain.Label;
import gov.ttb.labelverification.domain.LabelStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * "Lazy status recovery" — computes the true status at read time, so no cron
 * job is needed. Callers persist the transition when it differs.
 * <ul>
 *   <li>PROCESSING stuck &gt; 5 min → PENDING_REVIEW (crashed/timed-out pipeline)</li>
 *   <li>NEEDS_CORRECTION past deadline → REJECTED</li>
 *   <li>CONDITIONALLY_APPROVED past deadline → NEEDS_CORRECTION</li>
 * </ul>
 */
public final class EffectiveStatus {

    public static final Duration STALE_PROCESSING = Duration.ofMinutes(5);

    private EffectiveStatus() {
    }

    public static LabelStatus of(Label label, Clock clock) {
        return of(label.getStatus(), label.getCorrectionDeadline(), label.isDeadlineExpired(),
                label.getUpdatedAt(), clock);
    }

    public static LabelStatus of(LabelStatus status, Instant correctionDeadline, boolean deadlineExpired,
                                 Instant updatedAt, Clock clock) {
        Instant now = clock.instant();

        if (status == LabelStatus.PROCESSING && updatedAt != null
                && Duration.between(updatedAt, now).compareTo(STALE_PROCESSING) > 0) {
            return LabelStatus.PENDING_REVIEW;
        }
        if (correctionDeadline == null) {
            return status;
        }
        boolean passed = deadlineExpired || !correctionDeadline.isAfter(now);
        if (!passed) {
            return status;
        }
        return switch (status) {
            case NEEDS_CORRECTION -> LabelStatus.REJECTED;
            case CONDITIONALLY_APPROVED -> LabelStatus.NEEDS_CORRECTION;
            default -> status;
        };
    }
}
