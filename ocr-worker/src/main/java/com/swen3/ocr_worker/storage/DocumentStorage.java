package com.swen3.ocr_worker.storage;

import java.nio.file.Path;

/**
 * Where the original uploaded files live. The OCR listener depends on this interface only,
 * so tests can replace MinIO with a fake.
 */
public interface DocumentStorage {

    /**
     * Copies the object into a new temporary file and returns its path.
     * The caller owns the file and must delete it when done.
     *
     * @throws DocumentNotFoundException if no object exists under that key (permanent, retrying will not help)
     * @throws StorageException          if the storage could not be reached or read (possibly temporary)
     */
    Path download(String objectKey);
}
