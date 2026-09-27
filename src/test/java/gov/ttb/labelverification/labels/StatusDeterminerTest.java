package gov.ttb.labelverification.labels;

import static org.assertj.core.api.Assertions.assertThat;

import gov.ttb.labelverification.domain.ItemStatus;
import gov.ttb.labelverification.domain.LabelStatus;
import gov.ttb.labelverification.labels.StatusDeterminer.FieldOutcome;
import gov.ttb.labelverification.regulatory.BeverageType;
import gov.ttb.labelverification.regulatory.FieldName;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatusDeterminerTest {

    private static FieldOutcome f(FieldName n, ItemStatus s) {
        return new FieldOutcome(n, s);
    }

    @Test
    void allMatchIsApproved() {
        StatusDecision d = StatusDeterminer.determine(List.of(
                f(FieldName.BRAND_NAME, ItemStatus.MATCH),
                f(FieldName.HEALTH_WARNING, ItemStatus.MATCH)), BeverageType.DISTILLED_SPIRITS, 750);
        assertThat(d).isEqualTo(new StatusDecision(LabelStatus.APPROVED, null));
    }

    @Test
    void illegalContainerSizeIsRejected() {
        StatusDecision d = StatusDeterminer.determine(List.of(), BeverageType.DISTILLED_SPIRITS, 740);
        assertThat(d.status()).isEqualTo(LabelStatus.REJECTED);
    }

    @Test
    void wineDoesNotAllowSpiritsOnlySizes() {
        assertThat(StatusDeterminer.determine(List.of(), BeverageType.WINE, 200).status())
                .isEqualTo(LabelStatus.REJECTED);
        assertThat(StatusDeterminer.determine(List.of(), BeverageType.DISTILLED_SPIRITS, 200).status())
                .isEqualTo(LabelStatus.APPROVED);
    }

    @Test
    void maltBeveragesHaveNoSizeRestriction() {
        assertThat(StatusDeterminer.determine(List.of(), BeverageType.MALT_BEVERAGE, 473).status())
                .isEqualTo(LabelStatus.APPROVED);
    }

    @Test
    void healthWarningProblemsReject() {
        assertThat(StatusDeterminer.determine(List.of(f(FieldName.HEALTH_WARNING, ItemStatus.MISMATCH)),
                BeverageType.WINE, 750).status()).isEqualTo(LabelStatus.REJECTED);
        assertThat(StatusDeterminer.determine(List.of(f(FieldName.HEALTH_WARNING, ItemStatus.NOT_FOUND)),
                BeverageType.WINE, 750).status()).isEqualTo(LabelStatus.REJECTED);
    }

    @Test
    void mandatoryMismatchNeedsCorrectionWith30Days() {
        StatusDecision d = StatusDeterminer.determine(List.of(f(FieldName.ALCOHOL_CONTENT, ItemStatus.MISMATCH)),
                BeverageType.DISTILLED_SPIRITS, 750);
        assertThat(d).isEqualTo(new StatusDecision(LabelStatus.NEEDS_CORRECTION, 30));
    }

    @Test
    void minorFieldMismatchIsConditionallyApprovedWith7Days() {
        StatusDecision d = StatusDeterminer.determine(List.of(f(FieldName.BRAND_NAME, ItemStatus.NEEDS_CORRECTION)),
                BeverageType.DISTILLED_SPIRITS, 750);
        assertThat(d).isEqualTo(new StatusDecision(LabelStatus.CONDITIONALLY_APPROVED, 7));
    }

    @Test
    void optionalFieldNotFoundIsIgnored() {
        assertThat(StatusDeterminer.determine(List.of(f(FieldName.AGE_STATEMENT, ItemStatus.NOT_FOUND)),
                BeverageType.DISTILLED_SPIRITS, 750).status()).isEqualTo(LabelStatus.APPROVED);
    }

    @Test
    void alcoholContentIsOptionalForMaltBeverages() {
        assertThat(StatusDeterminer.determine(List.of(f(FieldName.ALCOHOL_CONTENT, ItemStatus.MISMATCH)),
                BeverageType.MALT_BEVERAGE, 355).status()).isEqualTo(LabelStatus.CONDITIONALLY_APPROVED);
    }

    @Test
    void rejectionOutranksEverything() {
        assertThat(StatusDeterminer.determine(List.of(
                f(FieldName.BRAND_NAME, ItemStatus.NEEDS_CORRECTION),
                f(FieldName.NET_CONTENTS, ItemStatus.MISMATCH),
                f(FieldName.HEALTH_WARNING, ItemStatus.MISMATCH)), BeverageType.WINE, 750).status())
                .isEqualTo(LabelStatus.REJECTED);
    }
}
