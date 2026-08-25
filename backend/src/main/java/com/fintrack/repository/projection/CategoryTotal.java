package com.fintrack.repository.projection;

import com.fintrack.entity.TransactionType;

import java.math.BigDecimal;

/**
 * One row of a {@code GROUP BY category, type} aggregate.
 *
 * <p>Grouped by direction as well as category because a category may be BOTH:
 * lumping an investment purchase together with an investment payout would produce
 * a figure that means nothing.
 *
 * @param categoryId   id of the category
 * @param categoryName its display name, joined in so no second query is needed
 * @param type         the direction being summarised
 * @param total        sum of the amounts
 * @param count        how many transactions contributed
 */
public record CategoryTotal(
        Long categoryId,
        String categoryName,
        TransactionType type,
        BigDecimal total,
        Long count
) {
}
