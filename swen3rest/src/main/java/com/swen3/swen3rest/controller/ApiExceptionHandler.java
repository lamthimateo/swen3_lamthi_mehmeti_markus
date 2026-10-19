package com.swen3.swen3rest.controller;

import com.swen3.swen3rest.service.exception.ConflictException;
import com.swen3.swen3rest.service.exception.InvalidRequestException;
import com.swen3.swen3rest.service.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import static java.util.stream.Collectors.joining;

/**
 * Translates exceptions into HTTP responses for all controllers.
 *
 * The service layer throws plain exceptions (NotFoundException, ...) and knows nothing about HTTP;
 * this class decides which status code each one means. Errors are returned as RFC 9457 Problem Details:
 * {"type", "title", "status", "detail", "instance"}. Extending ResponseEntityExceptionHandler gives the
 * same format for Spring's own errors (missing parameter, wrong type, upload too large, ...).
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> handleNotFound(NotFoundException ex, WebRequest request) {
        return problem(ex, HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> handleConflict(ConflictException ex, WebRequest request) {
        return problem(ex, HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Object> handleInvalidRequest(InvalidRequestException ex, WebRequest request) {
        return problem(ex, HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // entity constraints, checked by Hibernate right before an insert/update (safety net behind the request checks)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        String detail = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .sorted()
                .collect(joining("; "));
        return problem(ex, HttpStatus.BAD_REQUEST, detail, request);
    }

    // @Valid request records (CreateLabelRequest, AttachLabelRequest): list every invalid field
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .sorted()
                .collect(joining("; "));
        return handleExceptionInternal(ex, ProblemDetail.forStatusAndDetail(status, detail), headers, status, request);
    }

    // constraints placed directly on controller parameters, e.g. @NotEmptyFile on the upload
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        String detail = ex.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .sorted()
                .collect(joining("; "));
        return handleExceptionInternal(ex, ProblemDetail.forStatusAndDetail(status, detail), headers, status, request);
    }

    // every error response built by this class passes through here, so this is the one place that logs them
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        String detail = body instanceof ProblemDetail problem ? problem.getDetail() : ex.getMessage();
        if (statusCode.is5xxServerError()) {
            log.error("Request {} failed with {}: {}", describe(request), statusCode.value(), detail, ex);
        } else {
            log.warn("Rejected request {} with {}: {}", describe(request), statusCode.value(), detail);
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private ResponseEntity<Object> problem(Exception ex, HttpStatus status, String detail, WebRequest request) {
        return handleExceptionInternal(ex, ProblemDetail.forStatusAndDetail(status, detail),
                new HttpHeaders(), status, request);
    }

    private static String describe(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            HttpServletRequest httpRequest = servletWebRequest.getRequest();
            return httpRequest.getMethod() + " " + httpRequest.getRequestURI();
        }
        return request.getDescription(false);
    }
}
