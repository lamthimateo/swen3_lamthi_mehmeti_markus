package com.swen3.ocr_worker.search;

/** Elasticsearch could not be reached or refused the document. */
public class IndexingException extends RuntimeException {

    public IndexingException(String message) {
        super(message);
    }

    public IndexingException(String message, Throwable cause) {
        super(message, cause);
    }
}
