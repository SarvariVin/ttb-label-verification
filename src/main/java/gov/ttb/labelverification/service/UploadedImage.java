package gov.ttb.labelverification.service;

/** An image received from a browser or API client, before validation. */
public record UploadedImage(String filename, String contentType, byte[] bytes) {
}
