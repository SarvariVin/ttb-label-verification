package gov.ttb.labelverification.ai;

public record PipelineMetrics(long ocrTimeMs, long classificationTimeMs, long mergeTimeMs, long totalTimeMs,
                              int wordCount, int imageCount, int inputTokens, int outputTokens, int totalTokens) {
}
