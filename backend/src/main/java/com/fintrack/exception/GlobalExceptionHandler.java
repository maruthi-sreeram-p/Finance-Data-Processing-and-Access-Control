package com.fintrack.exception;

import com.fintrack.dto.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Turns every exception that escapes a controller into the one {@link ApiError}
 * shape.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so that Spring MVC's own
 * failures -- unreadable body, wrong method, missing parameter, unsupported media
 * type -- are covered too. Without it those would still be rendered in Spring's
 * default {@code ProblemDetail} format, and clients would face two different error
 * shapes from the same API depending on how far the request got.
 *
 * <p>The guiding rule throughout: the client is told what it needs to fix its
 * request and nothing more. Stack traces, exception class names, SQL and constraint
 * names stay in the server log.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Handles everything the application raises deliberately.
     *
     * <p>One handler for the whole hierarchy: each exception already carries the
     * {@link ErrorCode} that determines its status, so adding a new exception type
     * needs no change here.
     */
    @ExceptionHandler(FinTrackException.class)
    public ResponseEntity<ApiError> handleFinTrackException(FinTrackException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();

        // Client mistakes are logged at debug, server-side problems at warn, so the
        // log stays useful instead of filling with routine 404s.
        if (errorCode.httpStatus().is5xxServerError()) {
            log.error("{} on {} {}", errorCode, request.getMethod(), request.getRequestURI(), ex);
        } else {
            log.debug("{} on {} {}: {}", errorCode, request.getMethod(), request.getRequestURI(), ex.getMessage());
        }

        return ResponseEntity
                .status(errorCode.httpStatus())
                .body(ApiError.of(errorCode, ex.getMessage(), request.getRequestURI()));
    }

    /**
     * Re-throws rather than handling.
     *
     * <p>Spring Security's {@code ExceptionTranslationFilter} is the only component
     * that can tell an anonymous caller (401 -- authenticate and try again) from an
     * authenticated one lacking a role (403 -- never going to work). Handling the
     * exception here would flatten both into a single status, and the catch-all
     * below would otherwise swallow it into a 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void rethrowAccessDenied(AccessDeniedException ex) {
        throw ex;
    }

    /**
     * A uniqueness or foreign-key constraint that no service-level check caught.
     *
     * <p>Reported as 409 rather than 500 because it is a conflict with existing
     * data, not a server fault. The constraint name is logged but never returned --
     * it would expose the schema.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex,
                                                                HttpServletRequest request) {
        log.warn("Database constraint violated on {} {}", request.getMethod(), request.getRequestURI(), ex);

        return ResponseEntity
                .status(ErrorCode.DUPLICATE_RESOURCE.httpStatus())
                .body(ApiError.of(
                        ErrorCode.DUPLICATE_RESOURCE,
                        "The request conflicts with existing data",
                        request.getRequestURI()));
    }

    /**
     * Last resort for anything unforeseen.
     *
     * <p>The full exception is logged at error level for whoever is on call; the
     * client gets a fixed, generic sentence. An accidental leak here -- a message
     * carrying a file path, a query, or a hostname -- is a real disclosure risk.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);

        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ApiError.of(
                        ErrorCode.INTERNAL_ERROR,
                        "An unexpected error occurred. Please try again later.",
                        request.getRequestURI()));
    }

    /**
     * Bean Validation failure on a request body.
     *
     * <p>Every rejected field is reported at once rather than one per round trip,
     * so a form can show all its errors after a single submission.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toFieldError)
                .toList();

        ApiError body = ApiError.of(
                ErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                pathOf(request),
                fieldErrors);

        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.httpStatus()).body(body);
    }

    /** Bean Validation failure on a method parameter, e.g. an {@code @Min} on a query value. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        ApiError body = ApiError.of(
                ErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                pathOf(request));

        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.httpStatus()).body(body);
    }

    /**
     * Re-renders every remaining Spring MVC exception in the {@link ApiError} shape.
     *
     * <p>This is the single hook through which the parent class emits its responses,
     * so overriding it converts all of them at once -- malformed JSON, wrong HTTP
     * method, missing parameter, unsupported content type -- without a handler each.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        if (body instanceof ApiError) {
            return super.handleExceptionInternal(ex, body, headers, statusCode, request);
        }

        log.debug("Request rejected on {}: {}", pathOf(request), ex.getMessage());

        ErrorCode errorCode = statusCode.is4xxClientError() ? ErrorCode.BAD_REQUEST : ErrorCode.INTERNAL_ERROR;

        // The framework message is not forwarded: for malformed JSON it can quote the
        // offending payload back to the caller, and for a type mismatch it names
        // internal classes.
        ApiError apiError = new ApiError(
                java.time.Instant.now(),
                statusCode.value(),
                errorCode.name(),
                describe(statusCode),
                pathOf(request),
                null);

        return super.handleExceptionInternal(ex, apiError, headers, statusCode, request);
    }

    private static ApiError.FieldError toFieldError(FieldError error) {
        return new ApiError.FieldError(
                error.getField(),
                error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage());
    }

    private static String describe(HttpStatusCode statusCode) {
        return statusCode.is4xxClientError()
                ? "The request could not be processed as submitted"
                : "An unexpected error occurred. Please try again later.";
    }

    private static String pathOf(WebRequest request) {
        return request instanceof ServletWebRequest servletWebRequest
                ? servletWebRequest.getRequest().getRequestURI()
                : request.getDescription(false);
    }
}
