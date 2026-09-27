package gov.ttb.labelverification.labels;

import static org.assertj.core.api.Assertions.assertThat;

import gov.ttb.labelverification.domain.LabelStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class EffectiveStatusTest {

    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void needsCorrectionPastDeadlineIsRejected() {
        assertThat(EffectiveStatus.of(LabelStatus.NEEDS_CORRECTION, NOW.minusSeconds(1), false, NOW, CLOCK))
                .isEqualTo(LabelStatus.REJECTED);
    }

    @Test
    void conditionalApprovalPastDeadlineNeedsCorrection() {
        assertThat(EffectiveStatus.of(LabelStatus.CONDITIONALLY_APPROVED, NOW, false, NOW, CLOCK))
                .isEqualTo(LabelStatus.NEEDS_CORRECTION);
    }

    @Test
    void futureDeadlineKeepsStatus() {
        assertThat(EffectiveStatus.of(LabelStatus.NEEDS_CORRECTION, NOW.plusSeconds(60), false, NOW, CLOCK))
                .isEqualTo(LabelStatus.NEEDS_CORRECTION);
    }

    @Test
    void staleProcessingSurfacesForReview() {
        Instant sixMinutesAgo = NOW.minus(Duration.ofMinutes(6));
        assertThat(EffectiveStatus.of(LabelStatus.PROCESSING, null, false, sixMinutesAgo, CLOCK))
                .isEqualTo(LabelStatus.PENDING_REVIEW);
        assertThat(EffectiveStatus.of(LabelStatus.PROCESSING, null, false, NOW.minusSeconds(30), CLOCK))
                .isEqualTo(LabelStatus.PROCESSING);
    }

    @Test
    void deadlineUrgency() {
        assertThat(Deadlines.info(NOW.plus(Duration.ofDays(10)), CLOCK).urgency()).isEqualTo(Deadlines.Urgency.GREEN);
        assertThat(Deadlines.info(NOW.plus(Duration.ofDays(3)), CLOCK).urgency()).isEqualTo(Deadlines.Urgency.AMBER);
        assertThat(Deadlines.info(NOW.plus(Duration.ofHours(5)), CLOCK).urgency()).isEqualTo(Deadlines.Urgency.RED);
        assertThat(Deadlines.info(NOW.minusSeconds(1), CLOCK).urgency()).isEqualTo(Deadlines.Urgency.EXPIRED);
        assertThat(Deadlines.info(null, CLOCK)).isNull();
    }

    @Test
    void slaStatusBands() {
        assertThat(SlaStatus.of(30, 50, true)).isEqualTo(SlaStatus.GREEN);
        assertThat(SlaStatus.of(45, 50, true)).isEqualTo(SlaStatus.AMBER);
        assertThat(SlaStatus.of(60, 50, true)).isEqualTo(SlaStatus.RED);
        assertThat(SlaStatus.of(70, 80, false)).isEqualTo(SlaStatus.AMBER);
    }
}
