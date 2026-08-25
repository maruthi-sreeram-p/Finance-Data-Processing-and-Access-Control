package com.fintrack.exception;

/** Raised when creating or renaming a record would break a uniqueness rule. */
public class DuplicateResourceException extends FinTrackException {

    public DuplicateResourceException(String message) {
        super(ErrorCode.DUPLICATE_RESOURCE, message);
    }

    public static DuplicateResourceException of(String resource, String field, Object value) {
        return new DuplicateResourceException(
                "%s already exists with %s: %s".formatted(resource, field, value));
    }
}
