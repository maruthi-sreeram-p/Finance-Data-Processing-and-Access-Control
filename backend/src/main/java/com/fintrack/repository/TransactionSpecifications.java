package com.fintrack.repository;

import com.fintrack.entity.Transaction;
import com.fintrack.entity.TransactionType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Composable predicates for the transaction listing.
 *
 * <p>Each filter is a self-contained {@link Specification} that returns null when
 * its argument is absent, and Spring Data drops null conjuncts. That is what lets
 * the caller chain every filter unconditionally instead of writing a branch per
 * combination -- eight optional filters would otherwise mean 256 query variants.
 *
 * <p>The predicates are built through the Criteria API, so every value becomes a
 * bound JDBC parameter. There is no string concatenation anywhere in this class,
 * and therefore no way for a query parameter to alter the shape of the SQL.
 */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    /**
     * Restricts results to one owner.
     *
     * <p>Not optional, unlike every other predicate here. The listing is always
     * scoped to exactly one ledger, decided by {@code LedgerAccessPolicy} before
     * the query is built; a null owner would silently widen the query to every
     * user in the system.
     */
    public static Specification<Transaction> ownedBy(Long userId) {
        return (root, query, builder) -> builder.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Transaction> hasType(TransactionType type) {
        return (root, query, builder) ->
                type == null ? null : builder.equal(root.get("type"), type);
    }

    public static Specification<Transaction> hasCategoryId(Long categoryId) {
        return (root, query, builder) ->
                categoryId == null ? null : builder.equal(root.get("category").get("id"), categoryId);
    }

    /** Inclusive lower bound on the transaction date. */
    public static Specification<Transaction> dateFrom(LocalDate from) {
        return (root, query, builder) ->
                from == null ? null : builder.greaterThanOrEqualTo(root.get("transactionDate"), from);
    }

    /** Inclusive upper bound on the transaction date. */
    public static Specification<Transaction> dateTo(LocalDate to) {
        return (root, query, builder) ->
                to == null ? null : builder.lessThanOrEqualTo(root.get("transactionDate"), to);
    }

    public static Specification<Transaction> amountAtLeast(BigDecimal minAmount) {
        return (root, query, builder) ->
                minAmount == null ? null : builder.greaterThanOrEqualTo(root.get("amount"), minAmount);
    }

    public static Specification<Transaction> amountAtMost(BigDecimal maxAmount) {
        return (root, query, builder) ->
                maxAmount == null ? null : builder.lessThanOrEqualTo(root.get("amount"), maxAmount);
    }
}
