package gov.ttb.labelverification.ai.local;

import gov.ttb.labelverification.ai.BeverageDetector;
import gov.ttb.labelverification.ai.ExtractedField;
import gov.ttb.labelverification.ai.ExtractionPipeline;
import gov.ttb.labelverification.ai.ExtractionResult;
import gov.ttb.labelverification.ai.LabelImageData;
import gov.ttb.labelverification.ai.PipelineMetrics;
import gov.ttb.labelverification.ai.compare.ComparisonResult;
import gov.ttb.labelverification.ai.compare.FieldComparator;
import gov.ttb.labelverification.ai.compare.OcrTextSearch;
import gov.ttb.labelverification.ai.ocr.TesseractOcrEngine;
import gov.ttb.labelverification.regulatory.BeverageType;
import gov.ttb.labelverification.regulatory.FieldName;
import gov.ttb.labelverification.regulatory.MatchStrategy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Default, zero-cost pipeline: Tesseract OCR → search each expected value in
 * the OCR text. No API keys, no bounding boxes, no image-type classification.
 */
@Component
public class LocalExtractionPipeline implements ExtractionPipeline {

    public static final String ID = "local";
    public static final String MODEL = "tesseract-local";

    private final TesseractOcrEngine ocr;

    public LocalExtractionPipeline(TesseractOcrEngine ocr) {
        this.ocr = ocr;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isAvailable() {
        return ocr.isAvailable();
    }

    @Override
    public ExtractionResult extract(List<LabelImageData> images, BeverageType beverageType,
                                    Map<FieldName, String> expectedFields) {
        long start = System.nanoTime();

        // Stage 1: OCR each image
        List<String> ocrTexts = images.stream().map(img -> ocr.recognize(img.bytes()).fullText()).toList();
        long ocrMs = elapsedMs(start);

        StringBuilder combined = new StringBuilder();
        for (int i = 0; i < ocrTexts.size(); i++) {
            if (i > 0) {
                combined.append("\n\n");
            }
            combined.append("--- Image ").append(i + 1).append(" ---\n").append(ocrTexts.get(i));
        }
        String combinedText = combined.toString();

        // Stage 2: find each expected value in the OCR text
        long classifyStart = System.nanoTime();
        List<ExtractedField> fields = new ArrayList<>();
        for (Map.Entry<FieldName, String> entry : expectedFields.entrySet()) {
            FieldName field = entry.getKey();
            String expected = entry.getValue();
            boolean numeric = field.strategy() == MatchStrategy.NORMALIZED || field == FieldName.VINTAGE_YEAR;
            String found = OcrTextSearch.find(combinedText, expected, numeric);
            ComparisonResult comparison = FieldComparator.compare(field, expected, found);

            int imageIndex = 0;
            if (found != null && ocrTexts.size() > 1) {
                for (int i = 0; i < ocrTexts.size(); i++) {
                    if (OcrTextSearch.find(ocrTexts.get(i), expected, numeric) != null) {
                        imageIndex = i;
                        break;
                    }
                }
            }
            fields.add(new ExtractedField(field, found, comparison.confidence(), comparison.reasoning(), null, imageIndex));
        }
        long classifyMs = elapsedMs(classifyStart);
        long totalMs = elapsedMs(start);

        PipelineMetrics metrics = new PipelineMetrics(ocrMs, classifyMs, 0, totalMs,
                combinedText.split("\\s+").length, images.size(), 0, 0, 0);

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("ocrText", combinedText);
        raw.put("metrics", metrics);

        BeverageType detected = BeverageDetector.detect(combinedText);
        return new ExtractionResult(fields, List.of(), detected != null ? detected : beverageType,
                totalMs, MODEL, raw, metrics);
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
