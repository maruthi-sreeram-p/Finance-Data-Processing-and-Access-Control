package com.fintrack.exception;

/**
 * Raised when an authenticated caller is not permitted to perform an operation.
 *
 * <p>Used where hiding the resource would be misleading rather than protective --
 * for instance an administrator trying to delete their own account, where the row
 * plainly exists and the caller already knows it. For another user's data, prefer
 * {@link ResourceNotFoundException}.
 */
public class ForbiddenException extends FinTrackException {

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
