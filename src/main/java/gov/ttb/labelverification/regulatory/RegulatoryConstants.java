package gov.ttb.labelverification.regulatory;

import java.util.Set;

/** Application-wide constants that encode TTB process rules. */
public final class RegulatoryConstants {

    public static final String APP_NAME = "TTB Label Verification";
    public static final String APP_TAGLINE = "COLA label compliance review for TTB specialists";

    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    public static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    public static final int MAX_IMAGES_PER_LABEL = 6;
    public static final int MAX_BATCH_ROWS = 50;
    public static final int MAX_BATCH_APPROVE = 100;

    /** Correction window after a "needs correction" decision. */
    public static final int CORRECTION_DEADLINE_DAYS = 30;
    /** Correction window after a "conditionally approved" decision. */
    public static final int CONDITIONAL_DEADLINE_DAYS = 7;

    /** Mismatches on these fields are minor → conditionally approved (7-day window). */
    public static final Set<FieldName> MINOR_DISCREPANCY_FIELDS = Set.of(
            FieldName.BRAND_NAME, FieldName.FANCIFUL_NAME,
            FieldName.APPELLATION_OF_ORIGIN, FieldName.GRAPE_VARIETAL);

    /** A missing or mismatched value on these fields rejects the label outright. */
    public static final Set<FieldName> REJECTION_FIELDS = Set.of(FieldName.HEALTH_WARNING);

    private RegulatoryConstants() {
    }
}
