package gov.ttb.labelverification.ai.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import gov.ttb.labelverification.ai.PipelineException;
import gov.ttb.labelverification.config.AppProperties;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cloud OCR via Google Cloud Vision TEXT_DETECTION (REST, API-key auth).
 * Returns word-level bounding polygons, which the cloud pipeline uses to draw
 * pixel-accurate field overlays.
 */
@Component
public class GoogleVisionOcrEngine implements OcrEngine {

    private static final String ENDPOINT = "https://vision.googleapis.com/v1/images:annotate";

    private final RestClient restClient;
    private final AppProperties.Cloud cloud;

    public GoogleVisionOcrEngine(RestClient.Builder builder, AppProperties properties) {
        this.restClient = builder.baseUrl(ENDPOINT).build();
        this.cloud = properties.cloud();
    }

    @Override
    public boolean isAvailable() {
        return cloud.hasGoogleVision();
    }

    @Override
    public OcrResult recognize(byte[] image) {
        Map<String, Object> body = Map.of("requests", List.of(Map.of(
                "image", Map.of("content", Base64.getEncoder().encodeToString(image)),
                "features", List.of(Map.of("type", "TEXT_DETECTION")))));
        JsonNode response;
        try {
            response = restClient.post()
                    .uri(uri -> uri.queryParam("key", cloud.googleVisionApiKey()).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new PipelineException("Google Vision request failed: " + e.getMessage(), e);
        }
        return parse(response);
    }

    static OcrResult parse(JsonNode response) {
        JsonNode first = response == null ? null : response.path("responses").path(0);
        if (first == null || first.has("error")) {
            String msg = first == null ? "empty response" : first.path("error").path("message").asText();
            throw new PipelineException("Google Vision error: " + msg, null);
        }
        JsonNode annotations = first.path("textAnnotations");
        if (!annotations.isArray() || annotations.isEmpty()) {
            return new OcrResult("", List.of(), 0, 0);
        }
        String fullText = annotations.get(0).path("description").asText("");
        List<OcrWord> words = new ArrayList<>();
        // Index 0 is the whole block; the rest are individual words.
        for (int i = 1; i < annotations.size(); i++) {
            JsonNode a = annotations.get(i);
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = 0, maxY = 0;
            for (JsonNode v : a.path("boundingPoly").path("vertices")) {
                int x = v.path("x").asInt(0);
                int y = v.path("y").asInt(0);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
            if (minX == Integer.MAX_VALUE) {
                continue;
            }
            words.add(new OcrWord(a.path("description").asText(), minX, minY, maxX - minX, maxY - minY));
        }
        JsonNode page = first.path("fullTextAnnotation").path("pages").path(0);
        return new OcrResult(fullText, words, page.path("width").asInt(0), page.path("height").asInt(0));
    }
}
