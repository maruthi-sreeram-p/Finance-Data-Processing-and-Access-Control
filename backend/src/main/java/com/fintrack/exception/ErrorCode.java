package com.fintrack.exception;

import org.springframework.http.HttpStatus;

/**
 * The closed set of machine-readable error identifiers the API can return, each
 * bound to the HTTP status it maps to.
 *
 * <p>Pairing the code with its status here keeps the two from drifting apart and
 * spares {@code GlobalExceptionHandler} a long switch. Clients are expected to
 * branch on {@link #name()}, which is stable, rather than on the human-readable
 * message, which is not.
 */
public enum ErrorCode {

    /** The requested record does not exist, or the caller may not know that it does. */
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** A uniqueness rule was violated, e.g. an email that is already registered. */
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT),

    /** No credentials were presented, or they were invalid or expired. */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),

    /** The caller is authenticated but not permitted to perform this operation. */
    FORBIDDEN(HttpStatus.FORBIDDEN),

    /** The request breaks a domain rule, e.g. an expense filed under an income category. */
    INVALID_TRANSACTION(HttpStatus.BAD_REQUEST),

    /** Bean Validation rejected the request body or parameters. */
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),

    /** The request is syntactically or semantically malformed. */
    BAD_REQUEST(HttpStatus.BAD_REQUEST),

    /** Anything unhandled. The cause is logged; the client is told nothing about it. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public int statusValue() {
        return httpStatus.value();
    }
}
