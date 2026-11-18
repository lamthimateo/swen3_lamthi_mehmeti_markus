package com.swen3.swen3rest.storage;

import java.io.InputStream;

/**
 * Where the original uploaded files are kept. The service layer depends on this interface only,
 * so tests can use a mock and the storage technology (MinIO) can be replaced.
 */
public interface FileStorage {

    /**
     * Stores the content under the given key, replacing an existing object with the same key.
     *
     * @param size exact number of bytes in {@code content}
     * @throws StorageException if the file could not be stored
     */
    void store(String key, InputStream content, long size, String contentType);

    /** Removes the object; a failure is logged but never thrown, because callers use this for clean-up. */
    void deleteQuietly(String key);
}
