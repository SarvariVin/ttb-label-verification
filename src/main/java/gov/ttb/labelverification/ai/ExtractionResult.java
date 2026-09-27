package gov.ttb.labelverification.ai;

import gov.ttb.labelverification.regulatory.BeverageType;
import java.util.List;
import java.util.Map;

/**
 * Output of an {@link ExtractionPipeline} run.
 *
 * @param rawResponse pipeline-specific payload persisted for audit (OCR text, model output, metrics)
 */
public record ExtractionResult(List<ExtractedField> fields, List<ImageClassification> imageClassifications,
                               BeverageType detectedBeverageType, long processingTimeMs, String modelUsed,
                               Map<String, Object> rawResponse, PipelineMetrics metrics) {
}
