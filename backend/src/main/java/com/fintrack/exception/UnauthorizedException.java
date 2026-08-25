package com.fintrack.exception;

/** Raised when credentials are absent, invalid, expired, or belong to a blocked account. */
public class UnauthorizedException extends FinTrackException {

    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }
}
