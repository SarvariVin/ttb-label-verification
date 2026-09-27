package gov.ttb.labelverification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One AI pipeline run for a label. Re-analysis creates a new current result
 * and links the old one via {@code supersededBy}.
 */
@Entity
@Table(name = "validation_results")
public class ValidationResult extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "label_id")
    private Label label;

    @Column(name = "superseded_by")
    private String supersededBy;

    @Column(name = "is_current", nullable = false)
    private boolean current = true;

    /** Raw pipeline output (OCR text, model response, metrics) as JSON. */
    @Column(name = "ai_raw_response", nullable = false, columnDefinition = "TEXT")
    private String aiRawResponse;

    @Column(name = "processing_time_ms", nullable = false)
    private long processingTimeMs;

    @Column(name = "model_used", nullable = false)
    private String modelUsed;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    protected ValidationResult() {
    }

    public ValidationResult(Label label, String aiRawResponse, long processingTimeMs, String modelUsed,
                            Integer inputTokens, Integer outputTokens, Integer totalTokens) {
        this.label = label;
        this.aiRawResponse = aiRawResponse;
        this.processingTimeMs = processingTimeMs;
        this.modelUsed = modelUsed;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
    }

    public void supersede(String newResultId) {
        this.current = false;
        this.supersededBy = newResultId;
    }

    public Label getLabel() {
        return label;
    }

    public String getSupersededBy() {
        return supersededBy;
    }

    public boolean isCurrent() {
        return current;
    }

    public String getAiRawResponse() {
        return aiRawResponse;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }
}
