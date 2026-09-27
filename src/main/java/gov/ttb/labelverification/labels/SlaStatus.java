package gov.ttb.labelverification.labels;

import java.util.Collection;

/** Red/amber/green status for an SLA metric. */
public enum SlaStatus {
    GREEN("On target"), AMBER("Approaching limit"), RED("Over limit");

    private final String label;

    SlaStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * @param lowerIsBetter true for time/queue metrics, false for rates
     */
    public static SlaStatus of(double actual, double target, boolean lowerIsBetter) {
        if (lowerIsBetter) {
            if (actual <= target * 0.8) {
                return GREEN;
            }
            return actual <= target ? AMBER : RED;
        }
        if (actual >= target) {
            return GREEN;
        }
        return actual >= target * 0.8 ? AMBER : RED;
    }

    public static SlaStatus worst(Collection<SlaStatus> statuses) {
        if (statuses.contains(RED)) {
            return RED;
        }
        return statuses.contains(AMBER) ? AMBER : GREEN;
    }
}
