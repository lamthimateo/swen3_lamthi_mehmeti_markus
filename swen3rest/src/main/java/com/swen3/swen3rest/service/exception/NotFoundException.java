package com.swen3.swen3rest.service.exception;

/**
 * A requested document, label or label link does not exist.
 * Thrown by the service layer; ApiExceptionHandler turns it into HTTP 404.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
