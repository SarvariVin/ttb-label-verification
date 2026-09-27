package gov.ttb.labelverification.web.api.dto;

import gov.ttb.labelverification.domain.LabelStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusOverrideRequest(@NotNull LabelStatus newStatus,
                                    @NotNull @Size(min = 10, max = 2000) String justification,
                                    @Size(max = 50) String reasonCode) {
}
