package gov.ttb.labelverification.storage;

import gov.ttb.labelverification.regulatory.RegulatoryConstants;
import java.util.Arrays;

/**
 * Triple validation of uploads: declared MIME type, size, and magic bytes.
 * The magic-byte check is authoritative — the declared type is client-supplied.
 */
public final class ImageFileValidator {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};

    private ImageFileValidator() {
    }

    /** @return the detected content type */
    public static String validate(byte[] bytes, String declaredContentType, String filename) {
        if (bytes == null || bytes.length == 0) {
            throw new InvalidImageException(filename + ": file is empty");
        }
        if (bytes.length > RegulatoryConstants.MAX_FILE_SIZE_BYTES) {
            throw new InvalidImageException(filename + ": file exceeds 10 MB");
        }
        if (declaredContentType != null && !RegulatoryConstants.ALLOWED_IMAGE_TYPES.contains(declaredContentType)) {
            throw new InvalidImageException(filename + ": only JPEG, PNG and WebP images are allowed");
        }
        String detected = detect(bytes);
        if (detected == null) {
            throw new InvalidImageException(filename + ": file content is not a JPEG, PNG or WebP image");
        }
        return detected;
    }

    public static String detect(byte[] bytes) {
        if (startsWith(bytes, JPEG, 0)) {
            return "image/jpeg";
        }
        if (startsWith(bytes, PNG, 0)) {
            return "image/png";
        }
        if (startsWith(bytes, RIFF, 0) && startsWith(bytes, WEBP, 8)) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix, int offset) {
        return bytes.length >= offset + prefix.length
                && Arrays.equals(bytes, offset, offset + prefix.length, prefix, 0, prefix.length);
    }

    public static class InvalidImageException extends IllegalArgumentException {
        public InvalidImageException(String message) {
            super(message);
        }
    }
}
