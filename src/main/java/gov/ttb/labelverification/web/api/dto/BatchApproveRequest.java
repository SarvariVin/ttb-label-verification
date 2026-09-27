package gov.ttb.labelverification.web.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BatchApproveRequest(@NotEmpty @Size(max = 100) List<String> labelIds) {
}
