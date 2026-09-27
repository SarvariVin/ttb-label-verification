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

/** Immutable audit record of a label-level status change made by a specialist. */
@Entity
@Table(name = "status_overrides")
public class StatusOverride {

    @Id
    @Column(length = 21)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "label_id")
    private Label label;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specialist_id")
    private User specialist;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false)
    private LabelStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private LabelStatus newStatus;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String justification;

    @Column(name = "reason_code")
    private String reasonCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StatusOverride() {
    }

    public StatusOverride(Label label, User specialist, LabelStatus previousStatus, LabelStatus newStatus,
                          String justification, String reasonCode) {
        this.label = label;
        this.specialist = specialist;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.justification = justification;
        this.reasonCode = reasonCode;
    }

    @PrePersist
    void onCreate() {
        id = Ids.newId();
        createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public User getSpecialist() {
        return specialist;
    }

    public LabelStatus getPreviousStatus() {
        return previousStatus;
    }

    public LabelStatus getNewStatus() {
        return newStatus;
    }

    public String getJustification() {
        return justification;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
