package gov.ttb.labelverification.ai;

import gov.ttb.labelverification.regulatory.FieldName;

/**
 * A field value read from the label.
 *
 * @param value       text found on the label, or null if not found
 * @param confidence  0–100
 * @param boundingBox null for the local pipeline (Tesseract text search has no word geometry)
 * @param imageIndex  which uploaded image the value was found on
 */
public record ExtractedField(FieldName fieldName, String value, int confidence, String reasoning,
                             BoundingBox boundingBox, int imageIndex) {
}
