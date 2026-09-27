package gov.ttb.labelverification.service;

import gov.ttb.labelverification.domain.Label;
import gov.ttb.labelverification.domain.LabelStatus;
import gov.ttb.labelverification.labels.SlaStatus;
import gov.ttb.labelverification.repository.LabelRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dashboard SLA cards: queue depth, oldest waiting item, turnaround, AI agreement. */
@Service
public class SlaMetricsService {

    public record Metric(String name, String value, String target, SlaStatus status) {
    }

    public record Summary(List<Metric> metrics, SlaStatus overall, long pendingReview, long decidedTotal) {
    }

    private final LabelRepository labels;
    private final SettingsService settings;
    private final Clock clock;

    public SlaMetricsService(LabelRepository labels, SettingsService settings, Clock clock) {
        this.labels = labels;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        SettingsService.SlaTargets targets = settings.slaTargets();
        List<Label> all = labels.findAll();

        List<Label> pending = all.stream().filter(l -> l.getStatus() == LabelStatus.PENDING_REVIEW).toList();
        List<Label> decided = all.stream().filter(l -> LabelStatus.DECISIONS.contains(l.getStatus())).toList();

        double oldestHours = pending.stream()
                .mapToDouble(l -> hoursBetween(l.getCreatedAt(), clock.instant()))
                .max().orElse(0);
        double avgTurnaround = decided.stream()
                .mapToDouble(l -> hoursBetween(l.getCreatedAt(), l.getUpdatedAt()))
                .average().orElse(0);
        long agreed = decided.stream()
                .filter(l -> l.getAiProposedStatus() != null && l.getAiProposedStatus() == l.getStatus())
                .count();
        double agreement = decided.isEmpty() ? 100 : 100.0 * agreed / decided.size();

        List<Metric> metrics = List.of(
                new Metric("Queue depth", String.valueOf(pending.size()), "≤ " + targets.maxQueueDepth(),
                        SlaStatus.of(pending.size(), targets.maxQueueDepth(), true)),
                new Metric("Oldest in queue", "%.1f h".formatted(oldestHours), "≤ " + targets.reviewResponseHours() + " h",
                        SlaStatus.of(oldestHours, targets.reviewResponseHours(), true)),
                new Metric("Avg. turnaround", "%.1f h".formatted(avgTurnaround), "≤ " + targets.totalTurnaroundHours() + " h",
                        SlaStatus.of(avgTurnaround, targets.totalTurnaroundHours(), true)),
                new Metric("AI agreement", "%.0f%%".formatted(agreement), "≥ 80%",
                        SlaStatus.of(agreement, 80, false)));

        return new Summary(metrics, SlaStatus.worst(metrics.stream().map(Metric::status).toList()),
                pending.size(), decided.size());
    }

    private static double hoursBetween(java.time.Instant from, java.time.Instant to) {
        if (from == null || to == null) {
            return 0;
        }
        return Duration.between(from, to).toMinutes() / 60.0;
    }
}
