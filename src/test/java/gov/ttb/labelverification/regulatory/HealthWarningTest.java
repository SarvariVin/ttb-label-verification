package gov.ttb.labelverification.regulatory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HealthWarningTest {

    @Test
    void statutoryTextIsValid() {
        assertThat(HealthWarning.validate(HealthWarning.FULL_TEXT).valid()).isTrue();
    }

    @Test
    void lowercasePrefixIsFlagged() {
        HealthWarning.Check c = HealthWarning.validate(HealthWarning.FULL_TEXT.replace("GOVERNMENT WARNING:", "Government Warning:"));
        assertThat(c.valid()).isFalse();
        assertThat(c.issues()).anyMatch(i -> i.contains("ALL CAPS"));
    }

    @Test
    void missingSectionIsFlagged() {
        HealthWarning.Check c = HealthWarning.validate(HealthWarning.PREFIX + " " + HealthWarning.SECTION_1);
        assertThat(c.issues()).anyMatch(i -> i.contains("section (2)"));
    }

    @Test
    void emptyIsInvalid() {
        assertThat(HealthWarning.validate("  ").valid()).isFalse();
    }

    @Test
    void qualifyingPhrasesAreCaseInsensitive() {
        assertThat(QualifyingPhrases.isValid("distilled and bottled by")).isTrue();
        assertThat(QualifyingPhrases.isValid("poured by")).isFalse();
    }

    @Test
    void healthWarningTypeSize() {
        assertThat(BeverageType.healthWarningMinTypeSizeMm(187)).isEqualTo(1);
        assertThat(BeverageType.healthWarningMinTypeSizeMm(750)).isEqualTo(2);
        assertThat(BeverageType.healthWarningMinTypeSizeMm(3750)).isEqualTo(3);
    }
}
