package gov.ttb.labelverification.web.api.dto;

import gov.ttb.labelverification.domain.ItemStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReviewRequest(@NotNull @Valid List<FieldOverride> overrides) {

    public record FieldOverride(@NotBlank String validationItemId, @NotNull ItemStatus resolvedStatus,
                           @Size(max = 2000) String reviewerNotes) {
    }
}
