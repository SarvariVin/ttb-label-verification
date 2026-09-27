package gov.ttb.labelverification.ai.compare;

import static org.assertj.core.api.Assertions.assertThat;

import gov.ttb.labelverification.domain.ItemStatus;
import gov.ttb.labelverification.regulatory.FieldName;
import gov.ttb.labelverification.regulatory.HealthWarning;
import org.junit.jupiter.api.Test;

class OcrTextSearchTest {

    @Test
    void exactSubstringReturnsOcrSpan() {
        assertThat(OcrTextSearch.find("xx\nALDER CREST\nBourbon", "Alder Crest")).isEqualTo("ALDER CREST");
    }

    @Test
    void punctuationDropsAreTolerated() {
        assertThat(OcrTextSearch.find("STONES THROW distillery", "Stone's Throw")).isEqualTo("Stone's Throw");
    }

    @Test
    void garbledHealthWarningIsRecognisedByLandmarkAndBodyPhrases() {
        String ocr = """
                GOVERMENT WARNlNG: (1) Acc0rding to the surgeon general, women should not drink
                alcoholic beverages during pregnancy because of the risk of birth defects. (2) Consumptlon
                of alcoholic beverages impairs your ability to drive a car or operate machinery,
                and may cause health problems.""";
        assertThat(OcrTextSearch.find(ocr, HealthWarning.FULL_TEXT)).isEqualTo(HealthWarning.FULL_TEXT);
    }

    @Test
    void prefixAloneWithIllegibleBodyIsNotAMatch() {
        String ocr = "GOVERNMENT WARNING: ~~ %% ## unreadable small print ## %%";
        assertThat(OcrTextSearch.find(ocr, HealthWarning.FULL_TEXT)).isNull();
    }

    @Test
    void scatteredWordsOnDecorativeLabelsMatch() {
        String ocr = "ALDER\n~ ~ ~\nSMALL BATCH\nCREST\n6 YEARS";
        assertThat(OcrTextSearch.find(ocr, "Alder Crest")).isEqualTo("Alder Crest");
    }

    @Test
    void absentTextReturnsNull() {
        assertThat(OcrTextSearch.find("Tidewater Row Lager", "Alder Crest")).isNull();
    }

    // ---- Regressions found by the production sample run ----

    @Test
    void healthWarningMissingClauseTwoIsNotReportedAsTheFullText() {
        String truncated = HealthWarning.FULL_TEXT.substring(0, HealthWarning.FULL_TEXT.indexOf(" (2)"));
        String ocr = "SALT FLATS\nLager\nBrewed by\nSalt Flats Brewing, Ogden, Utah\n" + truncated;
        String found = OcrTextSearch.find(ocr, HealthWarning.FULL_TEXT);
        assertThat(found).isNotEqualTo(HealthWarning.FULL_TEXT).doesNotContain("operate machinery");
        assertThat(FieldComparator.compare(FieldName.HEALTH_WARNING, HealthWarning.FULL_TEXT, found).status())
                .isEqualTo(ItemStatus.MISMATCH);
    }

    @Test
    void garbledTitleCaseWarningPrefixIsStillCaught() {
        String ocr = HealthWarning.FULL_TEXT.replace("GOVERNMENT WARNING:", "Govemment Warning:")
                .replace("Surgeon", "Surgcon");
        String found = OcrTextSearch.find(ocr, HealthWarning.FULL_TEXT);
        ComparisonResult r = FieldComparator.compare(FieldName.HEALTH_WARNING, HealthWarning.FULL_TEXT, found);
        assertThat(r.status()).isEqualTo(ItemStatus.MISMATCH);
        assertThat(r.reasoning()).contains("capital letters");
    }

    @Test
    void similarAddressIsNotReportedAsTheDeclaredOne() {
        String ocr = "Distilled by\nSilver Heron Distillery, Portland, Maine\nGOVERNMENT WARNING:";
        String declared = "Silver Heron Distillery, Austin, Texas";
        String found = OcrTextSearch.find(ocr, declared);
        assertThat(found).isNotEqualTo(declared);
        assertThat(FieldComparator.compare(FieldName.NAME_AND_ADDRESS, declared, found).status())
                .isNotEqualTo(ItemStatus.MATCH);
    }

    @Test
    void numberIsNotFoundInsideALongerNumber() {
        String ocr = "Lager 4.5% Alc./Vol. 12 FL OZ";
        String found = OcrTextSearch.find(ocr, "5% Alc./Vol.", true);
        assertThat(found).contains("4.5%");
        assertThat(FieldComparator.compare(FieldName.ALCOHOL_CONTENT, "5% Alc./Vol.", found).status())
                .isEqualTo(ItemStatus.MISMATCH);
    }

    @Test
    void missingSpaceBetweenQuantityAndUnitIsTolerated() {
        String found = OcrTextSearch.find("40% Alc./Vol. (80 Proof) 1L\nBottled by", "1 L", true);
        assertThat(found).isEqualTo("1L");
        assertThat(FieldComparator.compare(FieldName.NET_CONTENTS, "1 L", found).status()).isEqualTo(ItemStatus.MATCH);
    }

    @Test
    void numericFieldsKeepTheValueActuallyOnTheLabel() {
        String ocr = "NORTHVALE Vodka 40% Alc./Vol. (80 Proof) 750 mL";
        // Numeric fields return what the label really says, for the comparator to reject.
        String found = OcrTextSearch.find(ocr, "42% Alc./Vol.", true);
        assertThat(found).contains("40%");
        assertThat(FieldComparator.compare(gov.ttb.labelverification.regulatory.FieldName.ALCOHOL_CONTENT,
                "42% Alc./Vol.", found).status()).isEqualTo(gov.ttb.labelverification.domain.ItemStatus.MISMATCH);
    }
}
