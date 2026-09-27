package gov.ttb.labelverification.web.api.dto;

import gov.ttb.labelverification.domain.ApplicationData;
import gov.ttb.labelverification.domain.ImageType;
import gov.ttb.labelverification.domain.ItemStatus;
import gov.ttb.labelverification.domain.LabelStatus;
import gov.ttb.labelverification.domain.ValidationItem;
import gov.ttb.labelverification.regulatory.BeverageType;
import gov.ttb.labelverification.regulatory.FieldName;
import gov.ttb.labelverification.service.LabelQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record LabelDetailView(String id, BeverageType beverageType, int containerSizeMl, LabelStatus status,
                              LabelStatus aiProposedStatus, BigDecimal overallConfidence, Instant correctionDeadline,
                              String applicant, String priorLabelId, Map<String, Object> applicationData,
                              List<ImageView> images, String modelUsed, Long processingTimeMs,
                              List<FieldResultView> fields, Instant createdAt) {

    public record ImageView(String id, String url, ImageType imageType, String filename) {
    }

    public record BoundingBoxView(BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height, BigDecimal angle) {
    }

    public record FieldResultView(String validationItemId, FieldName fieldName, String expectedValue,
                                  String extractedValue, ItemStatus status, BigDecimal confidence, String reasoning,
                                  String imageId, BoundingBoxView boundingBox) {

        static FieldResultView from(ValidationItem i, boolean includeScores) {
            return new FieldResultView(i.getId(), i.getFieldName(), i.getExpectedValue(), i.getExtractedValue(),
                    i.getStatus(), includeScores ? i.getConfidence() : null, includeScores ? i.getMatchReasoning() : null,
                    i.getLabelImageId(),
                    i.hasBoundingBox() ? new BoundingBoxView(i.getBboxX(), i.getBboxY(), i.getBboxWidth(),
                            i.getBboxHeight(), i.getBboxAngle()) : null);
        }
    }

    /**
     * @param includeScores applicants see extracted values and statuses, not confidence or AI reasoning
     */
    public static LabelDetailView from(LabelQueryService.LabelDetail d, boolean includeScores) {
        var label = d.label();
        return new LabelDetailView(label.getId(), label.getBeverageType(), label.getContainerSizeMl(),
                d.effectiveStatus(), includeScores ? label.getAiProposedStatus() : null,
                includeScores ? label.getOverallConfidence() : null, label.getCorrectionDeadline(),
                label.getApplicant() == null ? null : label.getApplicant().getCompanyName(),
                label.getPriorLabelId(),
                applicationData(d.applicationData()),
                d.images().stream().map(img -> new ImageView(img.getId(), "/api/v1/images/" + img.getId(),
                        img.getImageType(), img.getImageFilename())).toList(),
                d.currentResult() == null ? null : d.currentResult().getModelUsed(),
                d.currentResult() == null ? null : d.currentResult().getProcessingTimeMs(),
                d.items().stream().map(i -> FieldResultView.from(i, includeScores)).toList(),
                label.getCreatedAt());
    }

    private static Map<String, Object> applicationData(ApplicationData a) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (a == null) {
            return m;
        }
        m.put("serialNumber", a.getSerialNumber());
        m.put("classTypeCode", a.getClassTypeCode());
        for (FieldName f : FieldName.values()) {
            String v = a.valueOf(f);
            if (v != null) {
                m.put(f.key(), v);
            }
        }
        return m;
    }
}
