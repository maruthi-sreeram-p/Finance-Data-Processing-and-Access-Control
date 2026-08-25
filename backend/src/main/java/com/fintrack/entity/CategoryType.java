package com.fintrack.entity;

/**
 * Restricts which kind of transaction a category may be attached to.
 *
 * <p>Without this, nothing stops a client from filing a salary payment under
 * "Rent", which would quietly corrupt every dashboard aggregate. The rule is
 * enforced in the service layer, not by a database constraint, because it spans
 * two tables.
 */
public enum CategoryType {

    /** Usable only by INCOME transactions, e.g. Salary. */
    INCOME,

    /** Usable only by EXPENSE transactions, e.g. Rent. */
    EXPENSE,

    /** Usable by either direction, e.g. Investment or Other. */
    BOTH;

    /**
     * @return whether a transaction of the given type may use this category
     */
    public boolean accepts(TransactionType transactionType) {
        return switch (this) {
            case BOTH -> true;
            case INCOME -> transactionType == TransactionType.INCOME;
            case EXPENSE -> transactionType == TransactionType.EXPENSE;
        };
    }
}
