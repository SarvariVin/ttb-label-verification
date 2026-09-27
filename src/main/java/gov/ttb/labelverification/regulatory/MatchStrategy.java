package gov.ttb.labelverification.regulatory;

/** How an extracted label value is compared against the application value. */
public enum MatchStrategy {
    /** Whitespace-normalized, case-sensitive (health warning, vintage year). */
    EXACT,
    /** Dice-coefficient bigram similarity, threshold 0.8 (brand name, class/type). */
    FUZZY,
    /** Parsed to a canonical unit first (ABV %, mL, years). */
    NORMALIZED,
    /** Either value contains the other (country of origin). */
    CONTAINS,
    /** Mapped to a known enumerated value first (qualifying phrases). */
    ENUM
}
