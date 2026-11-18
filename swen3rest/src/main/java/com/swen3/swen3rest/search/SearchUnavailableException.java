package com.swen3.swen3rest.search;

/** Elasticsearch could not be reached or failed. ApiExceptionHandler turns it into HTTP 503. */
public class SearchUnavailableException extends RuntimeException {

    public SearchUnavailableException(String message) {
        super(message);
    }

    public SearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
