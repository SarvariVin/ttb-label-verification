package gov.ttb.labelverification.ai;

/** Raw image bytes handed to a pipeline. */
public record LabelImageData(byte[] bytes, String contentType) {
}
