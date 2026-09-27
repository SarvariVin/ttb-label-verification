package gov.ttb.labelverification.ai.ocr;

/** One recognized word with its pixel bounding box. */
public record OcrWord(String text, int x, int y, int width, int height) {
}
