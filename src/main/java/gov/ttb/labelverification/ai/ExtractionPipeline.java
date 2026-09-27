package gov.ttb.labelverification.ai;

import gov.ttb.labelverification.regulatory.BeverageType;
import gov.ttb.labelverification.regulatory.FieldName;
import java.util.List;
import java.util.Map;

/**
 * Reads label images and returns field values. Implementations:
 * {@code LocalExtractionPipeline} (Tesseract + text search, default) and
 * {@code CloudExtractionPipeline} (Google Vision + OpenAI, opt-in).
 */
public interface ExtractionPipeline {

    /** Stable identifier stored in settings ("local" / "cloud"). */
    String id();

    /** Whether the pipeline's dependencies (native lib, API keys) are present. */
    boolean isAvailable();

    /**
     * @param expectedFields application values, used to find/disambiguate fields on the label
     */
    ExtractionResult extract(List<LabelImageData> images, BeverageType beverageType,
                             Map<FieldName, String> expectedFields);
}
