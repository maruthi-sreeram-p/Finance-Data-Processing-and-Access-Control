package com.fintrack.dto.common;

import com.fintrack.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * The single error shape returned by every failing request.
 *
 * <p>One shape for all failures means a client can write one error handler. It is
 * used both by the security filter chain, which rejects requests before they reach
 * a controller, and by {@code GlobalExceptionHandler}, which handles everything
 * after -- otherwise a 401 and a 404 from the same API would look nothing alike.
 *
 * <p>There is no stack trace and no exception class name: internal detail is logged
 * on the server and never sent to the client.
 *
 * @param timestamp   when the failure occurred, in UTC
 * @param status      the HTTP status code
 * @param error       stable machine-readable code; clients should branch on this
 * @param message     human-readable explanation, safe to show to an end user
 * @param path        the request path that failed
 * @param fieldErrors per-field detail for validation failures; omitted otherwise
 */
@Schema(description = "Standard error response returned by every failing request")
public record ApiError(

        @Schema(description = "When the failure occurred (UTC)", example = "2026-08-24T09:15:30.123Z")
        Instant timestamp,

        @Schema(description = "HTTP status code", example = "404")
        int status,

        @Schema(description = "Stable machine-readable error code", example = "RESOURCE_NOT_FOUND")
        String error,

        @Schema(description = "Human-readable explanation", example = "Transaction not found with id: 10")
        String message,

        @Schema(description = "Request path that failed", example = "/api/transactions/10")
        String path,

        @Schema(description = "Per-field detail; present only for validation failures")
        List<FieldError> fieldErrors
) {

    /**
     * One rejected field of a request body or query string.
     *
     * @param field   the offending property name
     * @param message why it was rejected
     */
    @Schema(description = "A single rejected request field")
    public record FieldError(
            @Schema(description = "Property name", example = "amount")
            String field,

            @Schema(description = "Why the value was rejected", example = "Amount must be greater than zero")
            String message
    ) {
    }

    public static ApiError of(ErrorCode errorCode, String message, String path) {
        // Null rather than an empty list: Jackson is configured to omit nulls, so a
        // non-validation error carries no vestigial "fieldErrors": [] in its payload.
        return new ApiError(Instant.now(), errorCode.statusValue(), errorCode.name(), message, path, null);
    }

    public static ApiError of(ErrorCode errorCode, String message, String path, List<FieldError> fieldErrors) {
        return new ApiError(Instant.now(), errorCode.statusValue(), errorCode.name(), message, path, fieldErrors);
    }
}
