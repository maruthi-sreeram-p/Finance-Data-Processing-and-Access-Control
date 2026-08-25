package com.fintrack.exception;

/**
 * Raised when a transaction breaks a domain rule that Bean Validation cannot
 * express, because the rule spans more than one field or more than one table --
 * for example an EXPENSE filed under an INCOME-only category.
 */
public class InvalidTransactionException extends FinTrackException {

    public InvalidTransactionException(String message) {
        super(ErrorCode.INVALID_TRANSACTION, message);
    }
}
