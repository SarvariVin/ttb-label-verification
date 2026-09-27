package gov.ttb.labelverification.labels;

import static gov.ttb.labelverification.regulatory.RegulatoryConstants.CONDITIONAL_DEADLINE_DAYS;
import static gov.ttb.labelverification.regulatory.RegulatoryConstants.CORRECTION_DEADLINE_DAYS;

import gov.ttb.labelverification.domain.LabelStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Correction deadlines and their colour-coded urgency. */
public final class Deadlines {

    public enum Urgency { GREEN, AMBER, RED, EXPIRED }

    public record DeadlineInfo(long daysRemaining, Urgency urgency) {
    }

    private Deadlines() {
    }

    /** 7 days for conditionally approved, 30 for needs correction, otherwise none. */
    public static Instant correctionDeadlineFor(LabelStatus status, Clock clock) {
        return switch (status) {
            case CONDITIONALLY_APPROVED -> clock.instant().plus(Duration.ofDays(CONDITIONAL_DEADLINE_DAYS));
            case NEEDS_CORRECTION -> clock.instant().plus(Duration.ofDays(CORRECTION_DEADLINE_DAYS));
            default -> null;
        };
    }

    public static Instant plusDays(Integer days, Clock clock) {
        return days == null ? null : clock.instant().plus(Duration.ofDays(days));
    }

    /** green > 7 days, amber 1–7 days, red < 24 h, expired once passed. Null when no deadline. */
    public static DeadlineInfo info(Instant deadline, Clock clock) {
        if (deadline == null) {
            return null;
        }
        long remainingMs = deadline.toEpochMilli() - clock.millis();
        if (remainingMs <= 0) {
            return new DeadlineInfo(0, Urgency.EXPIRED);
        }
        long days = (long) Math.ceil(remainingMs / 86_400_000.0);
        Urgency urgency;
        if (remainingMs < 86_400_000L) {
            urgency = Urgency.RED;
        } else if (days <= 7) {
            urgency = Urgency.AMBER;
        } else {
            urgency = Urgency.GREEN;
        }
        return new DeadlineInfo(days, urgency);
    }
}
