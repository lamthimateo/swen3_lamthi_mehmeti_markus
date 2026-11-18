package com.swen3.ocr_worker.storage;

/** The requested object does not exist in storage. */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String message) {
        super(message);
    }
}
