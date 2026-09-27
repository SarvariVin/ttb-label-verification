package gov.ttb.labelverification.ai;

import gov.ttb.labelverification.domain.ImageType;

/** AI guess of which panel an image shows (front, back, neck…). Cloud pipeline only. */
public record ImageClassification(int imageIndex, ImageType imageType, int confidence) {
}
