package gov.ttb.labelverification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/** Immutable audit record of a specialist overriding one field's AI verdict. */
@Entity
@Table(name = "human_reviews")
public class HumanReview {

    @Id
    @Column(length = 21)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specialist_id")
    private User specialist;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "label_id")
    private Label label;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validation_item_id")
    private ValidationItem validationItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "original_status", nullable = false)
    private ItemStatus originalStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolved_status", nullable = false)
    private ItemStatus resolvedStatus;

    @Column(name = "reviewer_notes", columnDefinition = "TEXT")
    private String reviewerNotes;

    /** Optional specialist-drawn region {x,y,width,height} as JSON. */
    @Column(name = "annotation_data", columnDefinition = "TEXT")
    private String annotationData;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HumanReview() {
    }

    public HumanReview(User specialist, Label label, ValidationItem validationItem, ItemStatus originalStatus,
                       ItemStatus resolvedStatus, String reviewerNotes, String annotationData) {
        this.specialist = specialist;
        this.label = label;
        this.validationItem = validationItem;
        this.originalStatus = originalStatus;
        this.resolvedStatus = resolvedStatus;
        this.reviewerNotes = reviewerNotes;
        this.annotationData = annotationData;
    }

    @PrePersist
    void onCreate() {
        id = Ids.newId();
        createdAt = Instant.now();
        reviewedAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public User getSpecialist() {
        return specialist;
    }

    public ValidationItem getValidationItem() {
        return validationItem;
    }

    public ItemStatus getOriginalStatus() {
        return originalStatus;
    }

    public ItemStatus getResolvedStatus() {
        return resolvedStatus;
    }

    public String getReviewerNotes() {
        return reviewerNotes;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
