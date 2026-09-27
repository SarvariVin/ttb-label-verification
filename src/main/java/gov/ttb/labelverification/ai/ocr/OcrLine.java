package gov.ttb.labelverification.ai.ocr;

/**
 * One recognized text line.
 *
 * @param height line height in pixels (a proxy for font size)
 * @param top    distance from the top of the image, for reading order
 */
public record OcrLine(String text, int height, int top) {
}
