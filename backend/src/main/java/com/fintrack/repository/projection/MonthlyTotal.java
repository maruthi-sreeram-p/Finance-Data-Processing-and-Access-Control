package com.fintrack.repository.projection;

import com.fintrack.entity.TransactionType;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * One row of a {@code GROUP BY year, month, type} aggregate.
 *
 * <p>Year and month arrive as separate integers because that is what the SQL
 * EXTRACT function yields; {@link #yearMonth()} recombines them so the rest of the
 * application never handles the loose pair.
 *
 * @param year  calendar year of the transaction date
 * @param month calendar month, 1-12
 * @param type  the direction being summarised
 * @param total sum of the amounts
 */
public record MonthlyTotal(Integer year, Integer month, TransactionType type, BigDecimal total) {

    public YearMonth yearMonth() {
        return YearMonth.of(year, month);
    }
}
