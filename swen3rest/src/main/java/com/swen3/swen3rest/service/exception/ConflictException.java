package com.swen3.swen3rest.service.exception;

/**
 * The request clashes with existing data, e.g. a duplicate label name.
 * Thrown by the service layer; ApiExceptionHandler turns it into HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
