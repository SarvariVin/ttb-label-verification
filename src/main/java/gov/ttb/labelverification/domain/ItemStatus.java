package gov.ttb.labelverification.domain;

/** Per-field comparison outcome. */
public enum ItemStatus {
    MATCH,
    MISMATCH,
    NOT_FOUND,
    /** A mismatch on a minor field (brand, fanciful name, appellation, varietal). */
    NEEDS_CORRECTION
}
