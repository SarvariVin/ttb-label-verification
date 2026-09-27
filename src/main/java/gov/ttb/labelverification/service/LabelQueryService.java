package gov.ttb.labelverification.service;

import gov.ttb.labelverification.domain.ApplicationData;
import gov.ttb.labelverification.domain.HumanReview;
import gov.ttb.labelverification.domain.ItemStatus;
import gov.ttb.labelverification.domain.Label;
import gov.ttb.labelverification.domain.LabelImage;
import gov.ttb.labelverification.domain.LabelStatus;
import gov.ttb.labelverification.domain.StatusOverride;
import gov.ttb.labelverification.domain.ValidationItem;
import gov.ttb.labelverification.domain.ValidationResult;
import gov.ttb.labelverification.labels.Deadlines;
import gov.ttb.labelverification.labels.EffectiveStatus;
import gov.ttb.labelverification.regulatory.BeverageType;
import gov.ttb.labelverification.repository.HumanReviewRepository;
import gov.ttb.labelverification.repository.LabelImageRepository;
import gov.ttb.labelverification.repository.LabelRepository;
import gov.ttb.labelverification.repository.StatusOverrideRepository;
import gov.ttb.labelverification.repository.ValidationItemRepository;
import gov.ttb.labelverification.repository.ValidationResultRepository;
import gov.ttb.labelverification.security.AppUserPrincipal;
import gov.ttb.labelverification.storage.ImageStorage;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read models for dashboards and detail pages.
 * <p>
 * Data-level authorization lives here: specialists see every label, applicants
 * only their company's labels (others are reported as "not found").
 * <p>
 * Effective status is computed on read and persisted when it differs
 * ("lazy status recovery" — no scheduler required).
 */
@Service
public class LabelQueryService {

    public record LabelSummary(String id, String brandName, String fancifulName, BeverageType beverageType,
                               String applicantName, LabelStatus status, LabelStatus aiProposedStatus,
                               Integer overallConfidence, Instant createdAt, Deadlines.DeadlineInfo deadline,
                               boolean readyToApprove) {
    }

    public record LabelDetail(Label label, ApplicationData applicationData, LabelStatus effectiveStatus,
                              Deadlines.DeadlineInfo deadline, List<LabelImage> images,
                              List<ValidationItem> items, ValidationResult currentResult,
                              List<ValidationResult> resultHistory, List<HumanReview> reviews,
                              List<StatusOverride> overrides, boolean readyToApprove) {
    }

    public record Queues(List<LabelSummary> readyToApprove, List<LabelSummary> needsReview,
                         List<LabelSummary> all) {
    }

    public record ImageContent(byte[] bytes, String contentType) {
    }

    private final LabelRepository labels;
    private final LabelImageRepository images;
    private final ValidationResultRepository results;
    private final ValidationItemRepository items;
    private final HumanReviewRepository reviews;
    private final StatusOverrideRepository overrides;
    private final SettingsService settings;
    private final ImageStorage storage;
    private final Clock clock;

    public LabelQueryService(LabelRepository labels, LabelImageRepository images, ValidationResultRepository results,
                             ValidationItemRepository items, HumanReviewRepository reviews,
                             StatusOverrideRepository overrides, SettingsService settings, ImageStorage storage,
                             Clock clock) {
        this.labels = labels;
        this.images = images;
        this.results = results;
        this.items = items;
        this.reviews = reviews;
        this.overrides = overrides;
        this.settings = settings;
        this.storage = storage;
        this.clock = clock;
    }

    /** Specialist dashboard: Ready-to-Approve vs Needs-Review queues (oldest first) plus everything. */
    @Transactional
    public Queues specialistQueues() {
        int threshold = settings.approvalThreshold();
        List<LabelSummary> all = labels.findAllByOrderByCreatedAtDesc().stream()
                .map(l -> summarize(l, threshold)).toList();
        List<LabelSummary> pending = all.stream()
                .filter(s -> s.status() == LabelStatus.PENDING_REVIEW)
                .sorted((a, b) -> a.createdAt().compareTo(b.createdAt()))
                .toList();
        return new Queues(
                pending.stream().filter(LabelSummary::readyToApprove).toList(),
                pending.stream().filter(s -> !s.readyToApprove()).toList(),
                all);
    }

    /** Applicant dashboard: their own submissions, newest first. */
    @Transactional
    public List<LabelSummary> applicantSubmissions(AppUserPrincipal user) {
        if (user.applicantId() == null) {
            return List.of();
        }
        int threshold = settings.approvalThreshold();
        return labels.findByApplicantIdOrderByCreatedAtDesc(user.applicantId()).stream()
                .map(l -> summarize(l, threshold)).toList();
    }

    @Transactional
    public LabelDetail detail(AppUserPrincipal user, String labelId) {
        Label label = labels.findDetailedById(labelId).orElseThrow(() -> new NotFoundException("Label not found"));
        assertCanView(user, label);
        LabelStatus effective = applyEffectiveStatus(label);
        List<ValidationItem> current = items.findCurrentForLabel(labelId);
        List<ValidationResult> history = results.findByLabelIdOrderByCreatedAtDesc(labelId);
        ValidationResult currentResult = history.stream().filter(ValidationResult::isCurrent).findFirst().orElse(null);
        int threshold = settings.approvalThreshold();
        return new LabelDetail(label, label.getApplicationData(), effective,
                Deadlines.info(label.getCorrectionDeadline(), clock),
                images.findByLabelIdOrderBySortOrderAsc(labelId), current, currentResult, history,
                user.isSpecialist() ? reviews.findByLabelIdOrderByReviewedAtDesc(labelId) : List.of(),
                overrides.findByLabelIdOrderByCreatedAtDesc(labelId),
                isReadyToApprove(label, current, threshold));
    }

    @Transactional(readOnly = true)
    public ImageContent image(AppUserPrincipal user, String imageId) {
        LabelImage image = images.findById(imageId).orElseThrow(() -> new NotFoundException("Image not found"));
        assertCanView(user, image.getLabel());
        return new ImageContent(storage.load(image.getStorageKey()), image.getContentType());
    }

    // -----------------------------------------------------------------------

    private LabelSummary summarize(Label label, int threshold) {
        LabelStatus effective = applyEffectiveStatus(label);
        boolean ready = effective == LabelStatus.PENDING_REVIEW
                && isReadyToApprove(label, items.findCurrentForLabel(label.getId()), threshold);
        ApplicationData data = label.getApplicationData();
        return new LabelSummary(label.getId(),
                data == null ? "(unnamed)" : data.getBrandName(),
                data == null ? null : data.getFancifulName(),
                label.getBeverageType(),
                label.getApplicant() == null ? "—" : label.getApplicant().getCompanyName(),
                effective, label.getAiProposedStatus(),
                label.getOverallConfidence() == null ? null : label.getOverallConfidence().intValue(),
                label.getCreatedAt(), Deadlines.info(label.getCorrectionDeadline(), clock), ready);
    }

    /** Ready to Approve = pending review, AI proposed approval, confidence ≥ threshold, all fields match. */
    private static boolean isReadyToApprove(Label label, List<ValidationItem> current, int threshold) {
        return label.getStatus() == LabelStatus.PENDING_REVIEW
                && label.getAiProposedStatus() == LabelStatus.APPROVED
                && label.getOverallConfidence() != null
                && label.getOverallConfidence().intValue() >= threshold
                && !current.isEmpty()
                && current.stream().allMatch(i -> i.getStatus() == ItemStatus.MATCH);
    }

    /** Persists deadline / stale-processing transitions discovered at read time. */
    private LabelStatus applyEffectiveStatus(Label label) {
        LabelStatus effective = EffectiveStatus.of(label, clock);
        if (effective != label.getStatus()) {
            boolean deadlinePassed = label.getCorrectionDeadline() != null
                    && !label.getCorrectionDeadline().isAfter(clock.instant());
            label.setStatus(effective);
            if (deadlinePassed) {
                label.setDeadlineExpired(true);
                // A downgraded conditional approval gets a fresh correction window.
                label.setCorrectionDeadline(Deadlines.correctionDeadlineFor(effective, clock));
                if (label.getCorrectionDeadline() != null) {
                    label.setDeadlineExpired(false);
                }
            }
        }
        return effective;
    }

    private static void assertCanView(AppUserPrincipal user, Label label) {
        if (user.isSpecialist()) {
            return;
        }
        boolean own = label.getApplicant() != null && label.getApplicant().getId().equals(user.applicantId());
        if (!own) {
            throw new NotFoundException("Label not found");
        }
    }
}
