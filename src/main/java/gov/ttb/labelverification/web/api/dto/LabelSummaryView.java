package gov.ttb.labelverification.web.api.dto;

import gov.ttb.labelverification.domain.LabelStatus;
import gov.ttb.labelverification.regulatory.BeverageType;
import gov.ttb.labelverification.service.LabelQueryService;
import java.time.Instant;

public record LabelSummaryView(String id, String brandName, BeverageType beverageType, String applicant,
                               LabelStatus status, LabelStatus aiProposedStatus, Integer overallConfidence,
                               boolean readyToApprove, Instant createdAt, Long deadlineDaysRemaining) {

    public static LabelSummaryView from(LabelQueryService.LabelSummary s) {
        return new LabelSummaryView(s.id(), s.brandName(), s.beverageType(), s.applicantName(), s.status(),
                s.aiProposedStatus(), s.overallConfidence(), s.readyToApprove(), s.createdAt(),
                s.deadline() == null ? null : s.deadline().daysRemaining());
    }
}
