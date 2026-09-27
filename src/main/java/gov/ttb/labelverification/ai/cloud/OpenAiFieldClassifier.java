package gov.ttb.labelverification.ai.cloud;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.ttb.labelverification.ai.PipelineException;
import gov.ttb.labelverification.config.AppProperties;
import gov.ttb.labelverification.regulatory.BeverageType;
import gov.ttb.labelverification.regulatory.FieldName;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Stage 2 of the cloud pipeline: classifies OCR words into TTB fields using the
 * OpenAI Chat Completions API with a strict JSON-schema structured output.
 * Text-only input (no image tokens) keeps it fast and cheap.
 */
@Component
public class OpenAiFieldClassifier {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AppProperties.Cloud cloud;

    public OpenAiFieldClassifier(RestClient.Builder builder, ObjectMapper objectMapper, AppProperties properties) {
        this.cloud = properties.cloud();
        this.objectMapper = objectMapper;
        this.restClient = builder.baseUrl(cloud.openaiBaseUrl()).build();
    }

    public boolean isAvailable() {
        return cloud.hasOpenAi();
    }

    public String model() {
        return cloud.openaiModel();
    }

    /** Parsed model output plus token usage. */
    public record Classification(JsonNode output, int inputTokens, int outputTokens, int totalTokens) {
    }

    public Classification classify(BeverageType beverageType, List<ClassificationPrompts.IndexedWord> words,
                                   Map<FieldName, String> applicationData, int imageCount) {
        Map<String, Object> request = Map.of(
                "model", cloud.openaiModel(),
                "temperature", 0,
                "messages", List.of(
                        Map.of("role", "system", "content", ClassificationPrompts.system()),
                        Map.of("role", "user", "content",
                                ClassificationPrompts.user(beverageType, words, applicationData, imageCount))),
                "response_format", Map.of("type", "json_schema", "json_schema", Map.of(
                        "name", "label_classification", "strict", true, "schema", responseSchema())));

        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + cloud.openaiApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new PipelineException("OpenAI request failed: " + e.getMessage(), e);
        }
        if (response == null) {
            throw new PipelineException("OpenAI returned an empty response", null);
        }

        String content = response.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null) {
            throw new PipelineException("OpenAI response had no content (refusal or truncation)", null);
        }
        try {
            JsonNode usage = response.path("usage");
            return new Classification(objectMapper.readTree(content),
                    usage.path("prompt_tokens").asInt(0),
                    usage.path("completion_tokens").asInt(0),
                    usage.path("total_tokens").asInt(0));
        } catch (JsonProcessingException e) {
            throw new PipelineException("OpenAI returned invalid JSON", e);
        }
    }

    /** Strict structured-output schema: every property required, nullables typed as [T, "null"]. */
    static Map<String, Object> responseSchema() {
        List<String> fieldKeys = Arrays.stream(FieldName.values()).map(FieldName::key).toList();
        Map<String, Object> field = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("fieldName", "value", "confidence", "reasoning", "wordIndices"),
                "properties", Map.of(
                        "fieldName", Map.of("type", "string", "enum", fieldKeys),
                        "value", Map.of("type", List.of("string", "null")),
                        "confidence", Map.of("type", "integer"),
                        "reasoning", Map.of("type", List.of("string", "null")),
                        "wordIndices", Map.of("type", "array", "items", Map.of("type", "integer"))));
        Map<String, Object> imageClass = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("imageIndex", "imageType", "confidence"),
                "properties", Map.of(
                        "imageIndex", Map.of("type", "integer"),
                        "imageType", Map.of("type", "string", "enum", List.of("front", "back", "neck", "strip", "other")),
                        "confidence", Map.of("type", "integer")));
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("fields", "imageClassifications", "detectedBeverageType"),
                "properties", Map.of(
                        "fields", Map.of("type", "array", "items", field),
                        "imageClassifications", Map.of("type", "array", "items", imageClass),
                        "detectedBeverageType", Map.of("type", List.of("string", "null"),
                                "enum", Arrays.asList("DISTILLED_SPIRITS", "WINE", "MALT_BEVERAGE", null))));
    }
}
