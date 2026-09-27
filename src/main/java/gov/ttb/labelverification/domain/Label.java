package gov.ttb.labelverification.domain;

import gov.ttb.labelverification.regulatory.BeverageType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A label submission: images + Form 5100.31 data + AI and human verdicts. */
@Entity
@Table(name = "labels")
public class Label extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specialist_id")
    private User specialist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id")
    private Applicant applicant;

    /** Previous submission this one corrects (resubmission chain). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prior_label_id")
    private Label priorLabel;

    @Column(name = "prior_label_id", insertable = false, updatable = false)
    private String priorLabelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "beverage_type", nullable = false)
    private BeverageType beverageType;

    @Column(name = "container_size_ml", nullable = false)
    private int containerSizeMl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LabelStatus status = LabelStatus.PENDING;

    /** What the AI pipeline recommended; the specialist makes the final call. */
    @Enumerated(EnumType.STRING)
    @Column(name = "ai_proposed_status")
    private LabelStatus aiProposedStatus;

    @Column(name = "overall_confidence")
    private BigDecimal overallConfidence;

    @Column(name = "correction_deadline")
    private Instant correctionDeadline;

    @Column(name = "deadline_expired", nullable = false)
    private boolean deadlineExpired;

    @Column(name = "is_priority", nullable = false)
    private boolean priority;

    @OneToMany(mappedBy = "label", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<LabelImage> images = new ArrayList<>();

    @OneToOne(mappedBy = "label", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private ApplicationData applicationData;

    protected Label() {
    }

    public Label(Applicant applicant, BeverageType beverageType, int containerSizeMl, LabelStatus status) {
        this.applicant = applicant;
        this.beverageType = beverageType;
        this.containerSizeMl = containerSizeMl;
        this.status = status;
    }

    public void addImage(LabelImage image) {
        images.add(image);
        image.setLabel(this);
    }

    public void attachApplicationData(ApplicationData data) {
        this.applicationData = data;
        data.setLabel(this);
    }

    public User getSpecialist() {
        return specialist;
    }

    public void setSpecialist(User specialist) {
        this.specialist = specialist;
    }

    public Applicant getApplicant() {
        return applicant;
    }

    public Label getPriorLabel() {
        return priorLabel;
    }

    public void setPriorLabel(Label priorLabel) {
        this.priorLabel = priorLabel;
        this.priorLabelId = priorLabel == null ? null : priorLabel.getId();
    }

    public String getPriorLabelId() {
        return priorLabelId;
    }

    public BeverageType getBeverageType() {
        return beverageType;
    }

    public int getContainerSizeMl() {
        return containerSizeMl;
    }

    public LabelStatus getStatus() {
        return status;
    }

    public void setStatus(LabelStatus status) {
        this.status = status;
    }

    public LabelStatus getAiProposedStatus() {
        return aiProposedStatus;
    }

    public void setAiProposedStatus(LabelStatus aiProposedStatus) {
        this.aiProposedStatus = aiProposedStatus;
    }

    public BigDecimal getOverallConfidence() {
        return overallConfidence;
    }

    public void setOverallConfidence(BigDecimal overallConfidence) {
        this.overallConfidence = overallConfidence;
    }

    public Instant getCorrectionDeadline() {
        return correctionDeadline;
    }

    public void setCorrectionDeadline(Instant correctionDeadline) {
        this.correctionDeadline = correctionDeadline;
    }

    public boolean isDeadlineExpired() {
        return deadlineExpired;
    }

    public void setDeadlineExpired(boolean deadlineExpired) {
        this.deadlineExpired = deadlineExpired;
    }

    public boolean isPriority() {
        return priority;
    }

    public void setPriority(boolean priority) {
        this.priority = priority;
    }

    public List<LabelImage> getImages() {
        return images;
    }

    public ApplicationData getApplicationData() {
        return applicationData;
    }
}
