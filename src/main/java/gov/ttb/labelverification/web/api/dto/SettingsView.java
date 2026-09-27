package gov.ttb.labelverification.web.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record SettingsView(@NotNull @Pattern(regexp = "local|cloud") String pipelineModel,
                           @Min(50) @Max(100) int approvalThreshold,
                           @Positive int reviewResponseHours,
                           @Positive int totalTurnaroundHours,
                           @Positive int maxQueueDepth,
                           boolean cloudAvailable,
                           boolean localAvailable) {
}
