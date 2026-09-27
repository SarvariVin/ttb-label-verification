package gov.ttb.labelverification.service;

import gov.ttb.labelverification.ai.PipelineException;
import gov.ttb.labelverification.domain.HumanReview;
import gov.ttb.labelverification.domain.ItemStatus;
import gov.ttb.labelverification.domain.Label;
import gov.ttb.labelverification.domain.LabelStatus;
import gov.ttb.labelverification.domain.StatusOverride;
import gov.ttb.labelverification.domain.User;
import gov.ttb.labelverification.domain.ValidationItem;
import gov.ttb.labelverification.labels.Deadlines;
import gov.ttb.labelverification.labels.StatusDecision;
import gov.ttb.labelverification.labels.StatusDeterminer;
import gov.ttb.labelverification.regulatory.RegulatoryConstants;
import gov.ttb.labelverification.repository.HumanReviewRepository;
import gov.ttb.labelverification.repository.LabelRepository;
import gov.ttb.labelverification.repository.StatusOverrideRepository;
import gov.ttb.labelverification.repository.UserRepository;
import gov.ttb.labelverification.repository.ValidationItemRepository;
import gov.ttb.labelverification.security.AppUserPrincipal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Specialist decisions. Every change writes an immutable audit record
 * (human_reviews or status_overrides) before the label is updated.
 */
@Service
@PreAuthorize("hasRole('SPECIALIST')")
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    /** One field-level override. The original status is read from the DB, never trusted from the client. */
    public record FieldOverride(String validationItemId, ItemStatus resolvedStatus, String reviewerNotes) {
    }

    public record BatchApproveResult(int approvedCount, List<String> failedIds) {
        public boolean success() {
            return failedIds.isEmpty();
        }
    }

    private final LabelRepository labels;
    private final ValidationItemRepository items;
    private final HumanReviewRepository reviews;
    private final StatusOverrideRepository overrides;
    private final UserRepository users;
    private final SettingsService settings;
    private final LabelAnalysisService analysis;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ReviewService(LabelRepository labels, ValidationItemRepository items, HumanReviewRepository reviews,
                         StatusOverrideRepository overrides, UserRepository users, SettingsService settings,
                         LabelAnalysisService analysis, TransactionTemplate tx, Clock clock) {
        this.labels = labels;
        this.items = items;
        this.reviews = reviews;
        this.overrides = overrides;
        this.users = users;
        this.settings = settings;
        this.analysis = analysis;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * Applies field-level overrides, then re-derives the label status from the
     * resulting field statuses (container size is not re-checked on review).
     */
    @Transactional
    public LabelStatus submitReview(AppUserPrincipal principal, String labelId, List<FieldOverride> fieldOverrides) {
        Label label = labels.findById(labelId).orElseThrow(() -> new NotFoundException("Label not found"));
        if (!LabelStatus.REVIEWABLE.contains(label.getStatus())) {
            throw new BusinessRuleException("Label status \"" + label.getStatus().displayName()
                    + "\" is not eligible for review");
        }
        User specialist = users.getReferenceById(principal.id());

        List<ValidationItem> current = items.findCurrentForLabel(labelId);
        if (current.isEmpty()) {
            throw new BusinessRuleException("No validation result found for label");
        }
        Map<String, ValidationItem> byId = current.stream()
                .collect(Collectors.toMap(ValidationItem::getId, Function.identity()));

        for (FieldOverride o : fieldOverrides) {
            ValidationItem item = byId.get(o.validationItemId());
            if (item == null) {
                throw new BusinessRuleException("Invalid validation item");
            }
            if (o.resolvedStatus() == null || o.resolvedStatus() == ItemStatus.NEEDS_CORRECTION) {
                throw new BusinessRuleException("Resolved status must be MATCH, MISMATCH or NOT_FOUND");
            }
            if (o.resolvedStatus() == item.getStatus()) {
                continue;
            }
            reviews.save(new HumanReview(specialist, label, item, item.getStatus(), o.resolvedStatus(),
                    blankToNull(o.reviewerNotes()), null));
            item.setStatus(o.resolvedStatus());
        }

        List<StatusDeterminer.FieldOutcome> outcomes = current.stream()
                .map(i -> new StatusDeterminer.FieldOutcome(i.getFieldName(), i.getStatus()))
                .toList();
        StatusDecision decision = StatusDeterminer.determine(outcomes, label.getBeverageType(), null);

        label.setStatus(decision.status());
        label.setCorrectionDeadline(Deadlines.plusDays(decision.deadlineDays(), clock));
        label.setDeadlineExpired(false);
        label.setSpecialist(specialist);
        return decision.status();
    }

    /** Label-level override with a written justification (≥ 10 chars). */
    @Transactional
    public void overrideStatus(AppUserPrincipal principal, String labelId, LabelStatus newStatus,
                               String justification, String reasonCode) {
        if (newStatus == null || !LabelStatus.DECISIONS.contains(newStatus)) {
            throw new BusinessRuleException("Choose approved, conditionally approved, needs correction or rejected");
        }
        if (justification == null || justification.trim().length() < 10) {
            throw new BusinessRuleException("Justification must be at least 10 characters");
        }
        Label label = labels.findById(labelId).orElseThrow(() -> new NotFoundException("Label not found"));
        if (label.getStatus() == LabelStatus.PENDING || label.getStatus() == LabelStatus.PROCESSING) {
            throw new BusinessRuleException("Cannot override a label that is still being processed");
        }
        if (label.getStatus() == newStatus) {
            throw new BusinessRuleException("Label is already \"" + newStatus.displayName() + "\"");
        }
        User specialist = users.getReferenceById(principal.id());
        overrides.save(new StatusOverride(label, specialist, label.getStatus(), newStatus,
                justification.trim(), blankToNull(reasonCode)));

        label.setStatus(newStatus);
        label.setCorrectionDeadline(Deadlines.correctionDeadlineFor(newStatus, clock));
        label.setDeadlineExpired(false);
        label.setSpecialist(specialist);
    }

    /**
     * Approves labels that are PENDING_REVIEW, meet the confidence threshold and
     * have every field matching. Each label is its own transaction, so one
     * failure never rolls back the others.
     */
    public BatchApproveResult batchApprove(AppUserPrincipal principal, List<String> labelIds) {
        if (labelIds == null || labelIds.isEmpty()) {
            throw new BusinessRuleException("No labels provided");
        }
        if (labelIds.size() > RegulatoryConstants.MAX_BATCH_APPROVE) {
            throw new BusinessRuleException("Maximum " + RegulatoryConstants.MAX_BATCH_APPROVE + " labels per batch");
        }
        int threshold = settings.approvalThreshold();
        List<String> failed = new ArrayList<>();
        int approved = 0;

        for (String labelId : labelIds.stream().distinct().toList()) {
            try {
                Boolean ok = tx.execute(status -> approveIfEligible(principal, labelId, threshold));
                if (Boolean.TRUE.equals(ok)) {
                    approved++;
                } else {
                    failed.add(labelId);
                }
            } catch (RuntimeException e) {
                log.error("Batch approve failed for label {}", labelId, e);
                failed.add(labelId);
            }
        }
        return new BatchApproveResult(approved, failed);
    }

    private boolean approveIfEligible(AppUserPrincipal principal, String labelId, int threshold) {
        Label label = labels.findById(labelId).orElse(null);
        if (label == null || label.getStatus() != LabelStatus.PENDING_REVIEW) {
            return false;
        }
        int confidence = label.getOverallConfidence() == null ? 0 : label.getOverallConfidence().intValue();
        if (confidence < threshold) {
            return false;
        }
        List<ValidationItem> current = items.findCurrentForLabel(labelId);
        if (current.isEmpty() || current.stream().anyMatch(i -> i.getStatus() != ItemStatus.MATCH)) {
            return false;
        }
        User specialist = users.getReferenceById(principal.id());
        overrides.save(new StatusOverride(label, specialist, LabelStatus.PENDING_REVIEW, LabelStatus.APPROVED,
                "Batch approved — all fields verified by AI with high confidence", "batch_approved"));
        label.setStatus(LabelStatus.APPROVED);
        label.setCorrectionDeadline(null);
        label.setSpecialist(specialist);
        return true;
    }

    /** Re-runs the pipeline with current settings; the previous result is kept but superseded. */
    public LabelAnalysisService.Outcome reanalyze(String labelId) {
        LabelStatus status = tx.execute(s -> labels.findById(labelId)
                .map(Label::getStatus)
                .orElseThrow(() -> new NotFoundException("Label not found")));
        if (status == LabelStatus.PROCESSING) {
            throw new BusinessRuleException("Label is already being analyzed");
        }
        tx.executeWithoutResult(s -> labels.findById(labelId).ifPresent(l -> l.setStatus(LabelStatus.PROCESSING)));
        try {
            return analysis.analyze(labelId);
        } catch (PipelineException e) {
            tx.executeWithoutResult(s -> labels.findById(labelId).ifPresent(l -> l.setStatus(status)));
            throw new BusinessRuleException("Re-analysis failed: " + e.getMessage());
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
