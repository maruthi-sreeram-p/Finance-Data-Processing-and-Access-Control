package com.fintrack.exception;

/**
 * Raised when a requested record does not exist -- or exists but belongs to
 * someone else.
 *
 * <p>That second case is intentional. Answering "403 Forbidden" for another user's
 * transaction would confirm that the id is real, letting an attacker enumerate the
 * table one id at a time. Returning the same 404 as for a genuinely absent row
 * leaks nothing.
 */
public class ResourceNotFoundException extends FinTrackException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    /**
     * @param resource human-readable type name, e.g. {@code "Transaction"}
     * @param id       the identifier that was not found
     */
    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException("%s not found with id: %s".formatted(resource, id));
    }
}
