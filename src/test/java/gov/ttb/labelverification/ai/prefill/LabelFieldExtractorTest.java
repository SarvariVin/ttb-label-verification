package gov.ttb.labelverification.ai.prefill;

import static org.assertj.core.api.Assertions.assertThat;

import gov.ttb.labelverification.ai.ocr.OcrLine;
import gov.ttb.labelverification.regulatory.BeverageType;
import gov.ttb.labelverification.regulatory.FieldName;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Fixtures are the real Tesseract line output (text + pixel height) for the synthetic labels. */
class LabelFieldExtractorTest {

    private static final String[] WARNING = {
            "GOVERNMENT WARNING: (1) According to the Surgeon General,",
            "women should not drink alcoholic beverages during pregnancy",
            "because of the risk of birth defects. (2) Consumption of alcoholic",
            "beverages impairs your ability to drive a car or operate machinery,",
            "and may cause health problems."};

    private static List<OcrLine> lines(Object... heightTextPairs) {
        List<OcrLine> out = new ArrayList<>();
        int top = 100;
        for (int i = 0; i < heightTextPairs.length; i += 2) {
            out.add(new OcrLine((String) heightTextPairs[i + 1], (Integer) heightTextPairs[i], top));
            top += 100;
        }
        for (String w : WARNING) {
            out.add(new OcrLine(w, 39, top));
            top += 45;
        }
        return out;
    }

    @Test
    void bourbon() {
        LabelFieldExtractor.Result r = LabelFieldExtractor.extract(lines(
                104, "ALDERCREST",
                57, "Small Batch",
                69, "Kentucky Straight Bourbon Whiskey",
                55, "Aged 6 Years",
                61, "45% Alc./Vol. (90 Proof) 750 mL",
                49, "Distilled and Bottled by",
                49, "Aldercrest Distilling Co., Bardstown, Kentucky"));
        assertThat(r.beverageType()).isEqualTo(BeverageType.DISTILLED_SPIRITS);
        assertThat(r.containerSizeMl()).isEqualTo(750);
        assertThat(r.fields())
                .containsEntry(FieldName.BRAND_NAME, "ALDERCREST")
                .containsEntry(FieldName.FANCIFUL_NAME, "Small Batch")
                .containsEntry(FieldName.CLASS_TYPE, "Kentucky Straight Bourbon Whiskey")
                .containsEntry(FieldName.AGE_STATEMENT, "Aged 6 Years")
                .containsEntry(FieldName.ALCOHOL_CONTENT, "45% Alc./Vol. (90 Proof)")
                .containsEntry(FieldName.NET_CONTENTS, "750 mL")
                .containsEntry(FieldName.QUALIFYING_PHRASE, "Distilled and Bottled by")
                .containsEntry(FieldName.NAME_AND_ADDRESS, "Aldercrest Distilling Co., Bardstown, Kentucky");
        assertThat(r.fields().get(FieldName.HEALTH_WARNING)).startsWith("GOVERNMENT WARNING:").endsWith("health problems.");
    }

    @Test
    void lagerPrefersThePureClassLineOverTheFancifulName() {
        LabelFieldExtractor.Result r = LabelFieldExtractor.extract(lines(
                157, "TIDEWATER ROW",
                71, "Harbor Lager",
                65, "Lager",
                59, "5.0% Alc./Vol. 12 FL OZ",
                49, "Brewed and Packaged by",
                47, "Tidewater Row Brewing Co., Portland, Maine"));
        assertThat(r.beverageType()).isEqualTo(BeverageType.MALT_BEVERAGE);
        assertThat(r.containerSizeMl()).isEqualTo(355);
        assertThat(r.fields())
                .containsEntry(FieldName.BRAND_NAME, "TIDEWATER ROW")
                .containsEntry(FieldName.FANCIFUL_NAME, "Harbor Lager")
                .containsEntry(FieldName.CLASS_TYPE, "Lager")
                .containsEntry(FieldName.ALCOHOL_CONTENT, "5.0% Alc./Vol.")
                .containsEntry(FieldName.NET_CONTENTS, "12 FL OZ");
    }

    @Test
    void wineWithVintageAppellationAndSulfites() {
        LabelFieldExtractor.Result r = LabelFieldExtractor.extract(lines(
                120, "QUILLMOOR CELLARS",
                70, "Chardonnay",
                56, "Sonoma Coast - 2022 - Contains Sulfites",
                64, "13.5% Alc. by Vol. 750 mL",
                50, "Produced and Bottled by",
                50, "Quillmoor Cellars, Healdsburg, California"));
        assertThat(r.beverageType()).isEqualTo(BeverageType.WINE);
        assertThat(r.sulfiteDeclaration()).isTrue();
        assertThat(r.fields())
                .containsEntry(FieldName.BRAND_NAME, "QUILLMOOR CELLARS")
                .containsEntry(FieldName.CLASS_TYPE, "Chardonnay")
                .containsEntry(FieldName.GRAPE_VARIETAL, "Chardonnay")
                .containsEntry(FieldName.VINTAGE_YEAR, "2022")
                .containsEntry(FieldName.APPELLATION_OF_ORIGIN, "Sonoma Coast")
                .containsEntry(FieldName.ALCOHOL_CONTENT, "13.5% Alc. by Vol.")
                .doesNotContainKey(FieldName.FANCIFUL_NAME);
    }

    @Test
    void ampersandPhraseAndInlineAddress() {
        LabelFieldExtractor.Result r = LabelFieldExtractor.extract(lines(
                100, "HARBORVIEW",
                60, "India Pale Ale",
                50, "Brewed & Bottled by Harborview Brewing, Erie, PA",
                50, "ALC. 6.5% BY VOL. 16 FL OZ"));
        assertThat(r.fields())
                .containsEntry(FieldName.QUALIFYING_PHRASE, "Brewed & Bottled by")
                .containsEntry(FieldName.NAME_AND_ADDRESS, "Harborview Brewing, Erie, PA")
                .containsEntry(FieldName.CLASS_TYPE, "India Pale Ale")
                .containsEntry(FieldName.ALCOHOL_CONTENT, "ALC. 6.5% BY VOL.");
    }

    @Test
    void importedSpiritCountryOfOrigin() {
        LabelFieldExtractor.Result r = LabelFieldExtractor.extract(lines(
                110, "SOL DE ORO",
                60, "Tequila Blanco",
                50, "Product of Mexico",
                55, "40% Alc./Vol. 750 mL",
                50, "Imported by",
                50, "Sol de Oro Imports, San Diego, CA"));
        assertThat(r.fields())
                .containsEntry(FieldName.COUNTRY_OF_ORIGIN, "Product of Mexico")
                .containsEntry(FieldName.QUALIFYING_PHRASE, "Imported by")
                .containsEntry(FieldName.CLASS_TYPE, "Tequila Blanco");
    }

    @Test
    void emptyInputFindsNothing() {
        LabelFieldExtractor.Result r = LabelFieldExtractor.extract(List.of());
        assertThat(r.fields()).isEmpty();
        assertThat(r.beverageType()).isNull();
        assertThat(r.containerSizeMl()).isNull();
    }

    @Test
    void classScoreNeedsACoreWord() {
        assertThat(LabelFieldExtractor.classScore("Aldercrest Distilling Co., Bardstown, Kentucky")).isZero();
        assertThat(LabelFieldExtractor.classScore("Lager"))
                .isGreaterThan(LabelFieldExtractor.classScore("Harbor Lager"));
    }
}
