package com.swen3.swen3rest.storage;

/** The file storage could not be reached or refused the file. ApiExceptionHandler turns it into HTTP 503. */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
