package com.swen3.ocr_worker.storage;

/** Storage could not be read (network problem, server error, ...). */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
