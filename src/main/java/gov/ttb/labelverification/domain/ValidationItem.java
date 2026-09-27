package gov.ttb.labelverification.domain;

import gov.ttb.labelverification.regulatory.FieldName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Field-level comparison: expected (application) vs extracted (label) value. */
@Entity
@Table(name = "validation_items")
public class ValidationItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "validation_result_id")
    private ValidationResult validationResult;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "label_image_id")
    private LabelImage labelImage;

    /** Read-only FK so views can match items to images without loading the association. */
    @Column(name = "label_image_id", insertable = false, updatable = false)
    private String labelImageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_name", nullable = false)
    private FieldName fieldName;

    @Column(name = "expected_value", nullable = false, columnDefinition = "TEXT")
    private String expectedValue;

    @Column(name = "extracted_value", nullable = false, columnDefinition = "TEXT")
    private String extractedValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemStatus status;

    @Column(nullable = false)
    private BigDecimal confidence;

    @Column(name = "match_reasoning", columnDefinition = "TEXT")
    private String matchReasoning;

    /** Bounding box normalized to 0–1 (cloud pipeline only). */
    @Column(name = "bbox_x")
    private BigDecimal bboxX;
    @Column(name = "bbox_y")
    private BigDecimal bboxY;
    @Column(name = "bbox_width")
    private BigDecimal bboxWidth;
    @Column(name = "bbox_height")
    private BigDecimal bboxHeight;
    @Column(name = "bbox_angle")
    private BigDecimal bboxAngle;

    protected ValidationItem() {
    }

    public ValidationItem(ValidationResult validationResult, LabelImage labelImage, FieldName fieldName,
                          String expectedValue, String extractedValue, ItemStatus status,
                          BigDecimal confidence, String matchReasoning) {
        this.validationResult = validationResult;
        this.labelImage = labelImage;
        this.fieldName = fieldName;
        this.expectedValue = expectedValue;
        this.extractedValue = extractedValue;
        this.status = status;
        this.confidence = confidence;
        this.matchReasoning = matchReasoning;
    }

    public void setBoundingBox(BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height, BigDecimal angle) {
        this.bboxX = x;
        this.bboxY = y;
        this.bboxWidth = width;
        this.bboxHeight = height;
        this.bboxAngle = angle;
    }

    public boolean hasBoundingBox() {
        return bboxX != null && bboxY != null && bboxWidth != null && bboxHeight != null;
    }

    public ValidationResult getValidationResult() {
        return validationResult;
    }

    public LabelImage getLabelImage() {
        return labelImage;
    }

    public String getLabelImageId() {
        return labelImageId != null ? labelImageId : (labelImage == null ? null : labelImage.getId());
    }

    public FieldName getFieldName() {
        return fieldName;
    }

    public String getExpectedValue() {
        return expectedValue;
    }

    public String getExtractedValue() {
        return extractedValue;
    }

    public ItemStatus getStatus() {
        return status;
    }

    public void setStatus(ItemStatus status) {
        this.status = status;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public String getMatchReasoning() {
        return matchReasoning;
    }

    public BigDecimal getBboxX() {
        return bboxX;
    }

    public BigDecimal getBboxY() {
        return bboxY;
    }

    public BigDecimal getBboxWidth() {
        return bboxWidth;
    }

    public BigDecimal getBboxHeight() {
        return bboxHeight;
    }

    public BigDecimal getBboxAngle() {
        return bboxAngle;
    }
}
