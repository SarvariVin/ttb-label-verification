package gov.ttb.labelverification.storage;

/**
 * Where label images live. The default is the local filesystem; a production
 * deployment would swap in S3 / Azure Blob / GCS behind this interface.
 */
public interface ImageStorage {

    /** Stores bytes and returns an opaque storage key. */
    String store(byte[] bytes, String originalFilename, String contentType);

    byte[] load(String storageKey);
}
