package gov.ttb.labelverification.ai.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import gov.ttb.labelverification.ai.BoundingBox;
import gov.ttb.labelverification.ai.ExtractedField;
import gov.ttb.labelverification.ai.ExtractionPipeline;
import gov.ttb.labelverification.ai.ExtractionResult;
import gov.ttb.labelverification.ai.ImageClassification;
import gov.ttb.labelverification.ai.LabelImageData;
import gov.ttb.labelverification.ai.PipelineException;
import gov.ttb.labelverification.ai.PipelineMetrics;
import gov.ttb.labelverification.ai.ocr.GoogleVisionOcrEngine;
import gov.ttb.labelverification.ai.ocr.OcrResult;
import gov.ttb.labelverification.ai.ocr.OcrWord;
import gov.ttb.labelverification.domain.ImageType;
import gov.ttb.labelverification.regulatory.BeverageType;
import gov.ttb.labelverification.regulatory.FieldName;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * Opt-in hybrid pipeline:
 * <ol>
 *   <li>Stage 1 — Google Cloud Vision OCR on all images in parallel (word boxes)</li>
 *   <li>Stage 2 — OpenAI classifies the indexed word list into TTB fields</li>
 *   <li>Stage 3 — word indices are mapped back to OCR boxes → normalized field boxes</li>
 * </ol>
 * Boxes come from Vision, not the LLM, so overlays are pixel-accurate.
 */
@Component
public class CloudExtractionPipeline implements ExtractionPipeline {

    public static final String ID = "cloud";

    private final GoogleVisionOcrEngine ocr;
    private final OpenAiFieldClassifier classifier;

    public CloudExtractionPipeline(GoogleVisionOcrEngine ocr, OpenAiFieldClassifier classifier) {
        this.ocr = ocr;
        this.classifier = classifier;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isAvailable() {
        return ocr.isAvailable() && classifier.isAvailable();
    }

    @Override
    public ExtractionResult extract(List<LabelImageData> images, BeverageType beverageType,
                                    Map<FieldName, String> expectedFields) {
        if (!isAvailable()) {
            throw new PipelineException("Cloud pipeline is not configured (GOOGLE_VISION_API_KEY and OPENAI_API_KEY)", null);
        }
        long start = System.nanoTime();

        // Stage 1: OCR in parallel
        List<OcrResult> ocrResults = ocrAll(images);
        long ocrMs = elapsedMs(start);

        // Build global word index across images
        List<ClassificationPrompts.IndexedWord> indexed = new ArrayList<>();
        List<OcrWord> globalWords = new ArrayList<>();
        List<Integer> wordImage = new ArrayList<>();
        for (int img = 0; img < ocrResults.size(); img++) {
            for (OcrWord w : ocrResults.get(img).words()) {
                indexed.add(new ClassificationPrompts.IndexedWord(globalWords.size(), img, w.text()));
                globalWords.add(w);
                wordImage.add(img);
            }
        }

        // Stage 2: classify
        long classifyStart = System.nanoTime();
        OpenAiFieldClassifier.Classification classification =
                classifier.classify(beverageType, indexed, expectedFields, images.size());
        long classifyMs = elapsedMs(classifyStart);

        // Stage 3: merge bounding boxes
        long mergeStart = System.nanoTime();
        List<ExtractedField> fields = new ArrayList<>();
        for (JsonNode f : classification.output().path("fields")) {
            Optional<FieldName> name = FieldName.fromKey(f.path("fieldName").asText());
            if (name.isEmpty()) {
                continue;
            }
            List<Integer> indices = new ArrayList<>();
            f.path("wordIndices").forEach(i -> {
                int idx = i.asInt(-1);
                if (idx >= 0 && idx < globalWords.size()) {
                    indices.add(idx);
                }
            });
            int imageIndex = indices.isEmpty() ? 0 : wordImage.get(indices.get(0));
            List<OcrWord> sameImageWords = indices.stream()
                    .filter(i -> wordImage.get(i) == imageIndex).map(globalWords::get).toList();
            OcrResult imgOcr = ocrResults.get(imageIndex);
            BoundingBox box = BoundingBoxMath.unionNormalized(sameImageWords, imgOcr.imageWidth(), imgOcr.imageHeight());
            String value = f.path("value").isNull() ? null : f.path("value").asText();
            fields.add(new ExtractedField(name.get(), value, f.path("confidence").asInt(0),
                    f.path("reasoning").isNull() ? null : f.path("reasoning").asText(), box, imageIndex));
        }

        List<ImageClassification> imageClasses = new ArrayList<>();
        for (JsonNode ic : classification.output().path("imageClassifications")) {
            imageClasses.add(new ImageClassification(ic.path("imageIndex").asInt(),
                    ImageType.valueOf(ic.path("imageType").asText("other").toUpperCase(Locale.ROOT)),
                    ic.path("confidence").asInt(0)));
        }
        String detected = classification.output().path("detectedBeverageType").asText(null);
        long mergeMs = elapsedMs(mergeStart);
        long totalMs = elapsedMs(start);

        PipelineMetrics metrics = new PipelineMetrics(ocrMs, classifyMs, mergeMs, totalMs, globalWords.size(),
                images.size(), classification.inputTokens(), classification.outputTokens(), classification.totalTokens());
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("classification", classification.output());
        raw.put("metrics", metrics);

        return new ExtractionResult(fields, imageClasses,
                detected == null ? beverageType : BeverageType.valueOf(detected),
                totalMs, "google-vision+" + classifier.model(), raw, metrics);
    }

    private List<OcrResult> ocrAll(List<LabelImageData> images) {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<OcrResult>> futures = images.stream()
                    .map(img -> CompletableFuture.supplyAsync(() -> withImageSize(ocr.recognize(img.bytes()), img), pool))
                    .toList();
            return futures.stream().map(CompletableFuture::join).toList();
        } catch (CompletionException e) {
            if (e.getCause() instanceof PipelineException pe) {
                throw pe;
            }
            throw new PipelineException("OCR failed", e.getCause());
        }
    }

    /** Vision omits page size for some images; fall back to decoding the image. */
    private static OcrResult withImageSize(OcrResult result, LabelImageData img) {
        if (result.imageWidth() > 0 && result.imageHeight() > 0) {
            return result;
        }
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(img.bytes()));
            if (decoded != null) {
                return new OcrResult(result.fullText(), result.words(), decoded.getWidth(), decoded.getHeight());
            }
        } catch (IOException ignored) {
            // boxes will be omitted
        }
        return result;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
