package gov.ttb.labelverification.ai;

/**
 * Field location on a label image, normalized to 0–1 of image width/height.
 *
 * @param angle dominant text reading angle in degrees (0, 90, -90, 180)
 */
public record BoundingBox(double x, double y, double width, double height, double angle) {
}
