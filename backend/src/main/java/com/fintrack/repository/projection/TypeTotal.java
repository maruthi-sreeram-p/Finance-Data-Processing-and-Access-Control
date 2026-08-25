package com.fintrack.repository.projection;

import com.fintrack.entity.TransactionType;

import java.math.BigDecimal;

/**
 * One row of a {@code GROUP BY type} aggregate.
 *
 * <p>A record used as a JPQL constructor expression, so the database returns two
 * rows -- one for income, one for expenses -- instead of the whole ledger for the
 * application to add up. On an account with thousands of entries that is the
 * difference between a couple of index scans and loading every row into heap.
 *
 * @param type  the direction being summarised
 * @param total sum of the amounts, never null for a row that exists
 * @param count how many transactions contributed
 */
public record TypeTotal(TransactionType type, BigDecimal total, Long count) {
}
