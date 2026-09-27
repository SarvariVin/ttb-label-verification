package gov.ttb.labelverification.ai.ocr;

import java.util.List;

/**
 * @param words word-level geometry; empty when the engine only returns text
 */
public record OcrResult(String fullText, List<OcrWord> words, int imageWidth, int imageHeight) {
}
