package com.swen3.swen3rest.service.exception;

/**
 * The request breaks a business rule that Bean Validation cannot express,
 * e.g. sorting by a field that is not sortable.
 * Thrown by the service layer; ApiExceptionHandler turns it into HTTP 400.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
