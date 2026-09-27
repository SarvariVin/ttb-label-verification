package gov.ttb.labelverification.service;

import gov.ttb.labelverification.ai.ExtractedField;
import gov.ttb.labelverification.ai.ExtractionResult;
import gov.ttb.labelverification.ai.LabelImageData;
import gov.ttb.labelverification.ai.PipelineException;
import gov.ttb.labelverification.ai.cloud.CloudExtractionPipeline;
import gov.ttb.labelverification.ai.compare.TextNormalizer;
import gov.ttb.labelverification.ai.ocr.TesseractOcrEngine;
import gov.ttb.labelverification.ai.prefill.LabelFieldExtractor;
import gov.ttb.labelverification.regulatory.BeverageType;
import gov.ttb.labelverification.regulatory.FieldName;
import gov.ttb.labelverification.regulatory.RegulatoryConstants;
import gov.ttb.labelverification.storage.ImageFileValidator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Reads uploaded label images and suggests values for the submission form
 * ("pre-fill"). Nothing is stored. The health warning is never suggested: it is
 * always verified against the statutory text.
 * <p>
 * Uses the cloud pipeline when it is selected in settings and configured,
 * otherwise (or on cloud failure) local Tesseract + {@link LabelFieldExtractor}.
 */
@Service
public class PrefillService {

    private static final Logger log = LoggerFactory.getLogger(PrefillService.class);

    /**
     * @param fields form property name → suggested value (only fields that were found)
     */
    public record PrefillResult(BeverageType beverageType, Integer containerSizeMl, Boolean sulfiteDeclaration,
                                Map<String, String> fields, int filledCount, String source,
                                long processingTimeMs) {
    }

    private final TesseractOcrEngine tesseract;
    private final CloudExtractionPipeline cloud;
    private final SettingsService settings;

    public PrefillService(TesseractOcrEngine tesseract, CloudExtractionPipeline cloud, SettingsService settings) {
        this.tesseract = tesseract;
        this.cloud = cloud;
        this.settings = settings;
    }

    @PreAuthorize("hasRole('APPLICANT')")
    public PrefillResult extract(List<UploadedImage> uploads) {
        if (uploads == null || uploads.isEmpty()) {
            throw new BusinessRuleException("Select at least one label image");
        }
        if (uploads.size() > RegulatoryConstants.MAX_IMAGES_PER_LABEL) {
            throw new BusinessRuleException("At most " + RegulatoryConstants.MAX_IMAGES_PER_LABEL + " images per label");
        }
        List<LabelImageData> images = uploads.stream()
                .map(u -> new LabelImageData(u.bytes(), ImageFileValidator.validate(u.bytes(), u.contentType(), u.filename())))
                .toList();

        long start = System.nanoTime();
        if (CloudExtractionPipeline.ID.equals(settings.pipelineModel()) && cloud.isAvailable()) {
            try {
                return fromCloud(cloud.extract(images, null, Map.of()), start);
            } catch (PipelineException e) {
                log.warn("Cloud pre-fill failed, using local OCR: {}", e.getMessage());
            }
        }
        if (!tesseract.isAvailable()) {
            throw new PipelineException("No OCR engine is available to read the label", null);
        }
        return fromLocal(images, start);
    }

    private PrefillResult fromLocal(List<LabelImageData> images, long start) {
        BeverageType type = null;
        Integer size = null;
        Boolean sulfites = null;
        Map<FieldName, String> merged = new EnumMap<>(FieldName.class);
        // The first image (front) wins; later images only fill gaps.
        for (LabelImageData img : images) {
            LabelFieldExtractor.Result r = LabelFieldExtractor.extract(tesseract.recognizeLines(img.bytes()));
            r.fields().forEach(merged::putIfAbsent);
            type = type != null ? type : r.beverageType();
            size = size != null ? size : r.containerSizeMl();
            sulfites = sulfites != null ? sulfites : r.sulfiteDeclaration();
        }
        return build(type, size, sulfites, merged, "tesseract-local", start);
    }

    private PrefillResult fromCloud(ExtractionResult result, long start) {
        Map<FieldName, String> fields = new EnumMap<>(FieldName.class);
        for (ExtractedField f : result.fields()) {
            if (f.value() != null && !f.value().isBlank()) {
                fields.putIfAbsent(f.fieldName(), f.value().trim());
            }
        }
        Integer size = null;
        String net = fields.get(FieldName.NET_CONTENTS);
        Double ml = net == null ? null : TextNormalizer.normalizeNetContents(net);
        if (ml != null) {
            size = (int) Math.round(ml);
        }
        Boolean sulfites = fields.containsKey(FieldName.SULFITE_DECLARATION) ? Boolean.TRUE : null;
        return build(result.detectedBeverageType(), size, sulfites, fields, result.modelUsed(), start);
    }

    private static PrefillResult build(BeverageType type, Integer size, Boolean sulfites,
                                       Map<FieldName, String> fields, String source, long start) {
        Map<String, String> form = new LinkedHashMap<>();
        fields.forEach((field, value) -> {
            if (field != FieldName.HEALTH_WARNING && field != FieldName.SULFITE_DECLARATION
                    && field != FieldName.STANDARDS_OF_FILL) {
                form.put(field.formProperty(), value);
            }
        });
        int filled = form.size() + (type != null ? 1 : 0) + (size != null ? 1 : 0) + (sulfites != null ? 1 : 0);
        return new PrefillResult(type, size, sulfites, form, filled, source, (System.nanoTime() - start) / 1_000_000);
    }
}
