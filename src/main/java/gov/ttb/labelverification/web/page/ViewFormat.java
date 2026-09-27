package gov.ttb.labelverification.web.page;

import gov.ttb.labelverification.labels.Deadlines;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Formatting helpers for templates, used as {@code ${@fmt.dateTime(x)}}. */
@Component("fmt")
public class ViewFormat {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US).withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).withZone(ZoneId.systemDefault());

    public String dateTime(Instant instant) {
        return instant == null ? "—" : DATE_TIME.format(instant);
    }

    public String date(Instant instant) {
        return instant == null ? "—" : DATE.format(instant);
    }

    /** Enum → CSS modifier, e.g. NEEDS_CORRECTION → "needs-correction". */
    public String css(Enum<?> value) {
        return value == null ? "none" : value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Enum → sentence case, e.g. PENDING_REVIEW → "Pending review". */
    public String humanize(Enum<?> value) {
        if (value == null) {
            return "—";
        }
        String s = value.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** "Specialist Two" → "ST"; "labeling" → "L". */
    public String initials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(Locale.ROOT);
    }

    public String deadline(Deadlines.DeadlineInfo info) {
        if (info == null) {
            return "";
        }
        return info.urgency() == Deadlines.Urgency.EXPIRED ? "Deadline passed"
                : info.daysRemaining() + (info.daysRemaining() == 1 ? " day left" : " days left");
    }
}
