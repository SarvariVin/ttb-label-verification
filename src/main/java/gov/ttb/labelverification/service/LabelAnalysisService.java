package gov.ttb.labelverification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.ttb.labelverification.ai.BoundingBox;
import gov.ttb.labelverification.ai.ExtractedField;
import gov.ttb.labelverification.ai.ExtractionResult;
import gov.ttb.labelverification.ai.ImageClassification;
import gov.ttb.labelverification.ai.LabelImageData;
import gov.ttb.labelverification.ai.compare.ComparisonResult;
import gov.ttb.labelverification.ai.compare.FieldComparator;
import gov.ttb.labelverification.ai.compare.TextNormalizer;
import gov.ttb.labelverification.domain.AcceptedVariant;
import gov.ttb.labelverification.domain.ItemStatus;
import gov.ttb.labelverification.domain.Label;
import gov.ttb.labelverification.domain.LabelImage;
import gov.ttb.labelverification.domain.LabelStatus;
import gov.ttb.labelverification.domain.ValidationItem;
import gov.ttb.labelverification.domain.ValidationResult;
import gov.ttb.labelverification.labels.ExpectedFields;
import gov.ttb.labelverification.labels.StatusDecision;
import gov.ttb.labelverification.labels.StatusDeterminer;
import gov.ttb.labelverification.regulatory.BeverageType;
import gov.ttb.labelverification.regulatory.FieldName;
import gov.ttb.labelverification.regulatory.RegulatoryConstants;
import gov.ttb.labelverification.repository.AcceptedVariantRepository;
import gov.ttb.labelverification.repository.LabelRepository;
import gov.ttb.labelverification.repository.ValidationItemRepository;
import gov.ttb.labelverification.repository.ValidationResultRepository;
import gov.ttb.labelverification.storage.ImageStorage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Shared AI verification pipeline, used by submission, batch submission and
 * re-analysis.
 * <pre>
 *   load label + images ─▶ extract (local/cloud) ─▶ compare each field
 *        ─▶ persist result + items ─▶ derive AI-proposed status + confidence
 * </pre>
 * The AI call runs outside any DB transaction; reads and writes are two short
 * transactions on either side of it.
 */
@Service
public class LabelAnalysisService {

    /** Outcome of one analysis run. */
    public record Outcome(String validationResultId, LabelStatus proposedStatus, int overallConfidence,
                          String modelUsed) {
    }

    private record Inputs(BeverageType beverageType, int containerSizeMl, Map<FieldName, String> expected,
                          List<LabelImageData> images, List<String> imageIds) {
    }

    private final LabelRepository labels;
    private final ValidationResultRepository results;
    private final ValidationItemRepository items;
    private final AcceptedVariantRepository variants;
    private final ImageStorage storage;
    private final ExtractionService extraction;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public LabelAnalysisService(LabelRepository labels, ValidationResultRepository results,
                                ValidationItemRepository items, AcceptedVariantRepository variants,
                                ImageStorage storage, ExtractionService extraction, ObjectMapper objectMapper,
                                TransactionTemplate tx) {
        this.labels = labels;
        this.results = results;
        this.items = items;
        this.variants = variants;
        this.storage = storage;
        this.extraction = extraction;
        this.objectMapper = objectMapper;
        this.tx = tx;
    }

    /**
     * Runs the pipeline for a label and stores a new current validation result.
     * Sets the label to PENDING_REVIEW with the AI-proposed status — a
     * specialist always makes the final decision.
     */
    public Outcome analyze(String labelId) {
        Inputs inputs = tx.execute(status -> loadInputs(labelId));

        ExtractionResult extracted = extraction.extract(inputs.images(), inputs.beverageType(), inputs.expected());

        return tx.execute(status -> persist(labelId, inputs, extracted));
    }

    private Inputs loadInputs(String labelId) {
        Label label = labels.findDetailedById(labelId)
                .orElseThrow(() -> new NotFoundException("Label not found"));
        Map<FieldName, String> expected = ExpectedFields.from(label.getApplicationData(), label.getBeverageType());
        List<LabelImageData> images = new ArrayList<>();
        List<String> imageIds = new ArrayList<>();
        for (LabelImage img : label.getImages()) {
            images.add(new LabelImageData(storage.load(img.getStorageKey()), img.getContentType()));
            imageIds.add(img.getId());
        }
        if (images.isEmpty()) {
            throw new IllegalStateException("Label has no images");
        }
        return new Inputs(label.getBeverageType(), label.getContainerSizeMl(), expected, images, imageIds);
    }

    private Outcome persist(String labelId, Inputs inputs, ExtractionResult extracted) {
        Label label = labels.findDetailedById(labelId)
                .orElseThrow(() -> new NotFoundException("Label not found"));
        Map<String, LabelImage> imagesById = label.getImages().stream()
                .collect(Collectors.toMap(LabelImage::getId, Function.identity()));

        // 1. Image types from AI classification (≥ 60% confidence)
        for (ImageClassification ic : extracted.imageClassifications()) {
            if (ic.confidence() >= 60 && ic.imageIndex() < inputs.imageIds().size()) {
                imagesById.get(inputs.imageIds().get(ic.imageIndex())).setImageType(ic.imageType());
            }
        }

        // 2. Supersede the previous current result (re-analysis)
        ValidationResult result = new ValidationResult(label, toJson(extracted.rawResponse()),
                extracted.processingTimeMs(), extracted.modelUsed(), extracted.metrics().inputTokens(),
                extracted.metrics().outputTokens(), extracted.metrics().totalTokens());
        results.saveAndFlush(result);
        results.findByLabelIdOrderByCreatedAtDesc(labelId).stream()
                .filter(r -> !r.getId().equals(result.getId()) && r.isCurrent())
                .forEach(old -> old.supersede(result.getId()));

        // 3. Compare each expected field against what was read from the label
        Map<FieldName, ExtractedField> byName = extracted.fields().stream()
                .collect(Collectors.toMap(ExtractedField::fieldName, Function.identity(), (a, b) -> a));
        List<StatusDeterminer.FieldOutcome> outcomes = new ArrayList<>();
        int confidenceSum = 0;

        for (Map.Entry<FieldName, String> e : inputs.expected().entrySet()) {
            FieldName field = e.getKey();
            String expectedValue = e.getValue();
            ExtractedField ef = byName.get(field);
            String extractedValue = ef == null ? null : ef.value();

            ComparisonResult comparison = acceptedVariantMatch(field, expectedValue, extractedValue);
            if (comparison == null) {
                comparison = FieldComparator.compare(field, expectedValue, extractedValue);
            }
            ItemStatus itemStatus = comparison.status();
            if (itemStatus == ItemStatus.MISMATCH && RegulatoryConstants.MINOR_DISCREPANCY_FIELDS.contains(field)) {
                itemStatus = ItemStatus.NEEDS_CORRECTION;
            }

            int imageIndex = ef == null ? 0 : ef.imageIndex();
            String imageId = imageIndex < inputs.imageIds().size() ? inputs.imageIds().get(imageIndex)
                    : inputs.imageIds().get(0);

            ValidationItem item = new ValidationItem(result, imagesById.get(imageId), field, expectedValue,
                    extractedValue == null ? "" : extractedValue, itemStatus,
                    BigDecimal.valueOf(comparison.confidence()), comparison.reasoning());
            BoundingBox box = ef == null ? null : ef.boundingBox();
            if (box != null) {
                item.setBoundingBox(scale6(box.x()), scale6(box.y()), scale6(box.width()), scale6(box.height()),
                        BigDecimal.valueOf(box.angle()));
            }
            items.save(item);

            outcomes.add(new StatusDeterminer.FieldOutcome(field, itemStatus));
            confidenceSum += comparison.confidence();
        }

        // 4. Overall verdict + confidence
        StatusDecision decision = StatusDeterminer.determine(outcomes, inputs.beverageType(), inputs.containerSizeMl());
        int overallConfidence = outcomes.isEmpty() ? 0 : Math.round((float) confidenceSum / outcomes.size());

        label.setStatus(LabelStatus.PENDING_REVIEW);
        label.setAiProposedStatus(decision.status());
        label.setOverallConfidence(BigDecimal.valueOf(overallConfidence));

        return new Outcome(result.getId(), decision.status(), overallConfidence, extracted.modelUsed());
    }

    /** Specialist-maintained synonyms are accepted before any fuzzy logic runs. */
    private ComparisonResult acceptedVariantMatch(FieldName field, String expected, String extracted) {
        if (extracted == null || extracted.isBlank()) {
            return null;
        }
        String exp = TextNormalizer.normalizeWhitespace(expected);
        String ext = TextNormalizer.normalizeWhitespace(extracted);
        for (AcceptedVariant v : variants.findByFieldName(field)) {
            if (v.getCanonicalValue().equalsIgnoreCase(exp) && v.getVariantValue().equalsIgnoreCase(ext)) {
                return new ComparisonResult(ItemStatus.MATCH, 95,
                        field.key() + " matches an accepted variant (\"" + v.getVariantValue() + "\").");
            }
        }
        return null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static BigDecimal scale6(double v) {
        return BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP);
    }
}
