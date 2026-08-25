package com.fintrack.exception;

/**
 * Base class for every error this application raises deliberately.
 *
 * <p>Carrying an {@link ErrorCode} means the exception itself decides how it is
 * reported, so {@code GlobalExceptionHandler} needs a single handler for the whole
 * hierarchy instead of one per subclass. Anything that is <em>not</em> a
 * {@code FinTrackException} is by definition unexpected and becomes a 500 with a
 * generic message.
 */
public abstract class FinTrackException extends RuntimeException {

    private final ErrorCode errorCode;

    protected FinTrackException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected FinTrackException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
