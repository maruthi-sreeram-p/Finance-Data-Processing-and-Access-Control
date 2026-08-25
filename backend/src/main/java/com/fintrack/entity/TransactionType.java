package com.fintrack.entity;

/**
 * Direction of money movement for a transaction.
 *
 * <p>Amounts are always stored as positive values; this enum, not the sign of the
 * amount, determines whether a transaction adds to or subtracts from the balance.
 * Storing signed amounts would make aggregation queries ambiguous and make an
 * accidental sign flip silently corrupt a balance.
 */
public enum TransactionType {

    /** Money received: salary, freelance payment, investment return. */
    INCOME,

    /** Money spent: rent, groceries, bills. */
    EXPENSE
}
