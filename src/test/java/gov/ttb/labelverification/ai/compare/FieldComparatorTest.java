package gov.ttb.labelverification.ai.compare;

import static org.assertj.core.api.Assertions.assertThat;

import gov.ttb.labelverification.domain.ItemStatus;
import gov.ttb.labelverification.regulatory.FieldName;
import gov.ttb.labelverification.regulatory.HealthWarning;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FieldComparatorTest {

    @Test
    void missingValueIsNotFound() {
        ComparisonResult r = FieldComparator.compare(FieldName.BRAND_NAME, "Aldercrest", "  ");
        assertThat(r.status()).isEqualTo(ItemStatus.NOT_FOUND);
        assertThat(r.confidence()).isZero();
    }

    @Nested
    class Fuzzy {
        @Test
        void caseAndApostrophesMatch() {
            ComparisonResult r = FieldComparator.compare(FieldName.BRAND_NAME, "STONE'S THROW", "Stone's Throw");
            assertThat(r.status()).isEqualTo(ItemStatus.MATCH);
            assertThat(r.confidence()).isEqualTo(100);
        }

        @Test
        void partialOcrReadMatchesByContainment() {
            ComparisonResult r = FieldComparator.compare(FieldName.CLASS_TYPE,
                    "Kentucky Straight Bourbon Whiskey", "Straight Bourbon Whiskey");
            assertThat(r.status()).isEqualTo(ItemStatus.MATCH);
            assertThat(r.confidence()).isGreaterThanOrEqualTo(95);
        }

        @Test
        void differentBrandIsMismatch() {
            ComparisonResult r = FieldComparator.compare(FieldName.BRAND_NAME, "Aldercrest", "Northvale");
            assertThat(r.status()).isEqualTo(ItemStatus.MISMATCH);
        }
    }

    @Nested
    class Normalized {
        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "45% Alc./Vol. (90 Proof) | 45%",
                "45% | 90 Proof",
                "12.5% ABV | 12.5% Alc/Vol",
                "40% | 40.4%"})
        void alcoholContentMatches(String expected, String extracted) {
            assertThat(FieldComparator.compare(FieldName.ALCOHOL_CONTENT, expected, extracted).status())
                    .isEqualTo(ItemStatus.MATCH);
        }

        @Test
        void alcoholContentOutsideToleranceIsMismatch() {
            ComparisonResult r = FieldComparator.compare(FieldName.ALCOHOL_CONTENT, "45%", "40%");
            assertThat(r.status()).isEqualTo(ItemStatus.MISMATCH);
            assertThat(r.reasoning()).contains("expected 45%, found 40%");
        }

        @Test
        void halfAPointAlcoholDifferenceIsMismatch() {
            assertThat(FieldComparator.compare(FieldName.ALCOHOL_CONTENT, "6.0% Alc./Vol.", "5.5% Alc./Vol.").status())
                    .isEqualTo(ItemStatus.MISMATCH);
        }

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "750 mL | 750ml",
                "750 mL | 0.75L",
                "750 mL | 75 cL",
                "1 Liter | 1000 mL",
                "12 FL OZ | 355 mL"})
        void netContentsMatchesAcrossUnits(String expected, String extracted) {
            assertThat(FieldComparator.compare(FieldName.NET_CONTENTS, expected, extracted).status())
                    .isEqualTo(ItemStatus.MATCH);
        }

        @Test
        void netContentsDifferentSizeIsMismatch() {
            assertThat(FieldComparator.compare(FieldName.NET_CONTENTS, "750 mL", "1.75 L").status())
                    .isEqualTo(ItemStatus.MISMATCH);
        }

        @Test
        void ageStatementComparesYears() {
            assertThat(FieldComparator.compare(FieldName.AGE_STATEMENT, "12 Years Old", "Aged 12 years").status())
                    .isEqualTo(ItemStatus.MATCH);
            assertThat(FieldComparator.compare(FieldName.AGE_STATEMENT, "12 Years", "10 Years").status())
                    .isEqualTo(ItemStatus.MISMATCH);
        }
    }

    @Nested
    class Exact {
        @Test
        void healthWarningExactMatch() {
            ComparisonResult r = FieldComparator.compare(FieldName.HEALTH_WARNING,
                    HealthWarning.FULL_TEXT, HealthWarning.FULL_TEXT.replace(" ", "\n  "));
            assertThat(r.status()).isEqualTo(ItemStatus.MATCH);
            assertThat(r.confidence()).isEqualTo(100);
        }

        @Test
        void healthWarningPrefixNotInCapitalsIsMismatch() {
            ComparisonResult r = FieldComparator.compare(FieldName.HEALTH_WARNING,
                    HealthWarning.FULL_TEXT, HealthWarning.FULL_TEXT.replace("GOVERNMENT WARNING", "Government Warning"));
            assertThat(r.status()).isEqualTo(ItemStatus.MISMATCH);
            assertThat(r.reasoning()).contains("capital letters");
        }

        @Test
        void healthWarningBodyCaseDifferenceStillMatches() {
            ComparisonResult r = FieldComparator.compare(FieldName.HEALTH_WARNING,
                    HealthWarning.FULL_TEXT, HealthWarning.FULL_TEXT.toUpperCase());
            assertThat(r.status()).isEqualTo(ItemStatus.MATCH);
        }

        @Test
        void truncatedHealthWarningIsMismatch() {
            ComparisonResult r = FieldComparator.compare(FieldName.HEALTH_WARNING,
                    HealthWarning.FULL_TEXT, HealthWarning.PREFIX + " " + HealthWarning.SECTION_1);
            assertThat(r.status()).isEqualTo(ItemStatus.MISMATCH);
        }

        @Test
        void vintageYearComparesDigits() {
            assertThat(FieldComparator.compare(FieldName.VINTAGE_YEAR, "2019", "Vintage 2019").status())
                    .isEqualTo(ItemStatus.MATCH);
        }
    }

    @Test
    void countryOfOriginContains() {
        assertThat(FieldComparator.compare(FieldName.COUNTRY_OF_ORIGIN, "Scotland", "Product of Scotland").status())
                .isEqualTo(ItemStatus.MATCH);
    }

    @Test
    void qualifyingPhraseComparesKnownPhrases() {
        assertThat(FieldComparator.compare(FieldName.QUALIFYING_PHRASE, "Bottled by", "BOTTLED BY").status())
                .isEqualTo(ItemStatus.MATCH);
        assertThat(FieldComparator.compare(FieldName.QUALIFYING_PHRASE, "Bottled by", "Distilled by").status())
                .isEqualTo(ItemStatus.MISMATCH);
    }

    @Test
    void matchConfidenceIsNeverBelow95() {
        ComparisonResult r = FieldComparator.compare(FieldName.COUNTRY_OF_ORIGIN,
                "United States of America", "Made in the United States");
        assertThat(r.status()).isEqualTo(ItemStatus.MATCH);
        assertThat(r.confidence()).isGreaterThanOrEqualTo(95);
    }
}
