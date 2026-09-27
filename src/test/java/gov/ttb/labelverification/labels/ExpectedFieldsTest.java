package gov.ttb.labelverification.labels;

import static org.assertj.core.api.Assertions.assertThat;

import gov.ttb.labelverification.domain.ApplicationData;
import gov.ttb.labelverification.regulatory.BeverageType;
import gov.ttb.labelverification.regulatory.FieldName;
import gov.ttb.labelverification.regulatory.HealthWarning;
import org.junit.jupiter.api.Test;

class ExpectedFieldsTest {

    @Test
    void healthWarningIsAlwaysTheStatutoryText() {
        ApplicationData data = new ApplicationData();
        data.setBrandName("Northvale");
        // A non-compliant declared warning (e.g. copied from a defective label) must not become the reference.
        data.setHealthWarning(HealthWarning.FULL_TEXT.replace("GOVERNMENT WARNING:", "Government Warning:"));
        assertThat(ExpectedFields.from(data, BeverageType.DISTILLED_SPIRITS))
                .containsEntry(FieldName.HEALTH_WARNING, HealthWarning.FULL_TEXT)
                .containsEntry(FieldName.BRAND_NAME, "Northvale");
    }

    @Test
    void blankFieldsAreNotExpected() {
        ApplicationData data = new ApplicationData();
        data.setBrandName("Aldercrest");
        data.setFancifulName("  ");
        assertThat(ExpectedFields.from(data, BeverageType.WINE)).doesNotContainKey(FieldName.FANCIFUL_NAME);
    }

    @Test
    void sulfiteDeclarationBecomesExpectedText() {
        ApplicationData data = new ApplicationData();
        data.setBrandName("Quillmoor");
        data.setSulfiteDeclaration(true);
        assertThat(ExpectedFields.from(data, BeverageType.WINE))
                .containsEntry(FieldName.SULFITE_DECLARATION, "Contains Sulfites");
    }
}
