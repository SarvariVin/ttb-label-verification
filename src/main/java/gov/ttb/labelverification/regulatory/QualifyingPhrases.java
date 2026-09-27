package gov.ttb.labelverification.regulatory;

import java.util.List;
import java.util.Locale;

/**
 * Qualifying phrases for the Name and Address statement (Item 8).
 * Not a strict enum — the CFR says "appropriate phrase such as…" and allows
 * compound phrases (27 CFR 5.66(b), 7.66(b), 4.26).
 */
public final class QualifyingPhrases {

    public static final List<String> ALL = List.of(
            // Single-function
            "Bottled by", "Packed by", "Distilled by", "Blended by", "Produced by", "Prepared by",
            "Made by", "Manufactured by", "Imported by", "Brewed by",
            // Compound (entity performs multiple functions)
            "Distilled and Bottled by", "Produced and Bottled by", "Cellared and Bottled by",
            "Vinted and Bottled by", "Prepared and Bottled by", "Brewed and Bottled by",
            "Brewed and Packaged by", "Imported and Bottled by",
            // Contract / third-party bottling
            "Bottled for", "Distilled by and Bottled for", "Brewed and Bottled for",
            // Wine-specific
            "Estate Bottled");

    public static final List<String> LOWERCASE = ALL.stream().map(p -> p.toLowerCase(Locale.ROOT)).toList();

    private QualifyingPhrases() {
    }

    public static boolean isValid(String text) {
        return text != null && LOWERCASE.contains(text.trim().toLowerCase(Locale.ROOT));
    }
}
