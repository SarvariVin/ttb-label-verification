package gov.ttb.labelverification.service;

import gov.ttb.labelverification.ai.PipelineException;
import gov.ttb.labelverification.domain.Applicant;
import gov.ttb.labelverification.domain.ApplicationData;
import gov.ttb.labelverification.domain.ImageType;
import gov.ttb.labelverification.domain.Label;
import gov.ttb.labelverification.domain.LabelImage;
import gov.ttb.labelverification.domain.LabelStatus;
import gov.ttb.labelverification.regulatory.RegulatoryConstants;
import gov.ttb.labelverification.repository.ApplicantRepository;
import gov.ttb.labelverification.repository.LabelRepository;
import gov.ttb.labelverification.security.AppUserPrincipal;
import gov.ttb.labelverification.storage.ImageFileValidator;
import gov.ttb.labelverification.storage.ImageStorage;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applicant submission: validate images → store → create label (PROCESSING)
 * → run the AI pipeline → label becomes PENDING_REVIEW with an AI proposal.
 * The applicant gets instant feedback; the specialist never waits for AI.
 */
@Service
public class SubmissionService {

    private static final Logger log = LoggerFactory.getLogger(SubmissionService.class);

    /**
     * @param timedOut true when the pipeline timed out; the label is saved and can be re-analyzed
     */
    public record SubmissionResult(String labelId, LabelStatus status, LabelStatus aiProposedStatus,
                                   Integer overallConfidence, String error, boolean timedOut) {

        public boolean success() {
            return error == null;
        }
    }

    private final LabelRepository labels;
    private final ApplicantRepository applicants;
    private final ImageStorage storage;
    private final LabelAnalysisService analysis;
    private final TransactionTemplate tx;

    public SubmissionService(LabelRepository labels, ApplicantRepository applicants, ImageStorage storage,
                             LabelAnalysisService analysis, TransactionTemplate tx) {
        this.labels = labels;
        this.applicants = applicants;
        this.storage = storage;
        this.analysis = analysis;
        this.tx = tx;
    }

    @PreAuthorize("hasRole('APPLICANT')")
    public SubmissionResult submit(AppUserPrincipal user, LabelApplicationForm form, List<UploadedImage> uploads) {
        if (uploads == null || uploads.isEmpty()) {
            throw new BusinessRuleException("Upload at least one label image");
        }
        if (uploads.size() > RegulatoryConstants.MAX_IMAGES_PER_LABEL) {
            throw new BusinessRuleException("At most " + RegulatoryConstants.MAX_IMAGES_PER_LABEL + " images per label");
        }

        // 1. Validate (magic bytes) and store images before touching the database
        List<LabelImage> images = new ArrayList<>();
        for (int i = 0; i < uploads.size(); i++) {
            UploadedImage up = uploads.get(i);
            String detectedType = ImageFileValidator.validate(up.bytes(), up.contentType(), up.filename());
            String key = storage.store(up.bytes(), up.filename(), detectedType);
            images.add(new LabelImage(key, safeFilename(up.filename(), i), detectedType,
                    i == 0 ? ImageType.FRONT : ImageType.OTHER, i));
        }

        // 2. Create label + application data + images (PROCESSING)
        String labelId = tx.execute(status -> createLabel(user, form, images));

        // 3. Run the shared pipeline; on failure leave the label retryable
        try {
            LabelAnalysisService.Outcome outcome = analysis.analyze(labelId);
            return new SubmissionResult(labelId, LabelStatus.PENDING_REVIEW, outcome.proposedStatus(),
                    outcome.overallConfidence(), null, false);
        } catch (PipelineException e) {
            log.warn("Pipeline failed for label {}: {}", labelId, e.getMessage());
            markPending(labelId);
            String message = e.isTimeout()
                    ? "Analysis is taking longer than expected. Your submission has been saved and will be re-analyzed."
                    : "Your submission was saved, but automatic analysis failed. A specialist will review it.";
            return new SubmissionResult(labelId, LabelStatus.PENDING, null, null, message, e.isTimeout());
        }
    }

    private String createLabel(AppUserPrincipal user, LabelApplicationForm form, List<LabelImage> images) {
        Applicant applicant = user.applicantId() == null ? null
                : applicants.findById(user.applicantId()).orElse(null);
        Label label = new Label(applicant, form.getBeverageType(), form.getContainerSizeMl(), LabelStatus.PROCESSING);

        if (form.getPriorLabelId() != null && !form.getPriorLabelId().isBlank()) {
            Label prior = labels.findById(form.getPriorLabelId())
                    .orElseThrow(() -> new NotFoundException("Prior label not found"));
            if (prior.getApplicant() == null || applicant == null
                    || !prior.getApplicant().getId().equals(applicant.getId())) {
                throw new AccessDeniedException("Prior label belongs to another applicant");
            }
            label.setPriorLabel(prior);
        }

        label.attachApplicationData(toApplicationData(form));
        images.forEach(label::addImage);
        return labels.save(label).getId();
    }

    private void markPending(String labelId) {
        try {
            tx.executeWithoutResult(status -> labels.findById(labelId)
                    .ifPresent(l -> l.setStatus(LabelStatus.PENDING)));
        } catch (RuntimeException e) {
            // Label stays PROCESSING; EffectiveStatus surfaces it as PENDING_REVIEW after 5 minutes.
            log.error("Could not reset label {} to PENDING", labelId, e);
        }
    }

    static ApplicationData toApplicationData(LabelApplicationForm f) {
        ApplicationData d = new ApplicationData();
        d.setSerialNumber(blankToNull(f.getSerialNumber()));
        d.setBrandName(f.getBrandName().trim());
        d.setFancifulName(blankToNull(f.getFancifulName()));
        d.setClassType(blankToNull(f.getClassType()));
        d.setClassTypeCode(blankToNull(f.getClassTypeCode()));
        d.setAlcoholContent(blankToNull(f.getAlcoholContent()));
        d.setNetContents(blankToNull(f.getNetContents()));
        d.setHealthWarning(blankToNull(f.getHealthWarning()));
        d.setNameAndAddress(blankToNull(f.getNameAndAddress()));
        d.setQualifyingPhrase(blankToNull(f.getQualifyingPhrase()));
        d.setCountryOfOrigin(blankToNull(f.getCountryOfOrigin()));
        d.setGrapeVarietal(blankToNull(f.getGrapeVarietal()));
        d.setAppellationOfOrigin(blankToNull(f.getAppellationOfOrigin()));
        d.setVintageYear(blankToNull(f.getVintageYear()));
        d.setSulfiteDeclaration(f.getSulfiteDeclaration());
        d.setAgeStatement(blankToNull(f.getAgeStatement()));
        d.setStateOfDistillation(blankToNull(f.getStateOfDistillation()));
        return d;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String safeFilename(String name, int index) {
        if (name == null || name.isBlank()) {
            return "image-" + index;
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        return base.length() > 200 ? base.substring(0, 200) : base;
    }
}
