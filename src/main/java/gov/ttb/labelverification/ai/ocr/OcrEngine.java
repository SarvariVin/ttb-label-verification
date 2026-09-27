package gov.ttb.labelverification.ai.ocr;

/** Stage 1 of every pipeline: image bytes → text (and word geometry when available). */
public interface OcrEngine {

    boolean isAvailable();

    OcrResult recognize(byte[] image);
}
