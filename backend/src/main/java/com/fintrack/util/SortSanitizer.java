package com.fintrack.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

/**
 * Restricts client-supplied sorting to properties that are safe and useful to sort by.
 *
 * <p>{@code ?sort=} is bound straight into a JPA property path. Left unchecked a
 * client can sort by anything the entity exposes, including a lazy association --
 * {@code ?sort=user.passwordHash} is a valid path, and while it returns no hashes,
 * ordering by one lets an attacker binary-search their content across requests.
 * Unknown paths simply throw, turning a typo into a 500.
 *
 * <p>Anything not on the whitelist is dropped rather than rejected, so an unusable
 * sort degrades to the sensible default instead of failing the request.
 */
public final class SortSanitizer {

    /** Properties a client may sort the transaction listing by. */
    public static final Set<String> TRANSACTION_SORT_PROPERTIES =
            Set.of("transactionDate", "amount", "type", "createdAt", "updatedAt", "id");

    /**
     * Applied when the client asks for nothing, or asks only for properties that
     * were dropped. Most recent first is what a ledger view wants.
     */
    private static final Sort DEFAULT_TRANSACTION_SORT =
            Sort.by(Sort.Direction.DESC, "transactionDate");

    /**
     * Appended to every sort as the final key.
     *
     * <p>Without a unique tiebreaker, rows sharing a sort value have no defined
     * order between queries. Across a paginated scan that means the database is
     * free to return the same row on two pages and omit another entirely -- a real
     * and easily missed bug, not a theoretical one.
     */
    private static final Sort.Order STABLE_TIEBREAKER = Sort.Order.desc("id");

    private SortSanitizer() {
    }

    /**
     * Returns the page request with its sort reduced to allowed properties and made
     * deterministic.
     *
     * @param pageable       the page request bound from the query string
     * @param allowedProperties properties the client may sort by
     */
    public static Pageable sanitize(Pageable pageable, Set<String> allowedProperties) {
        List<Sort.Order> allowedOrders = pageable.getSort().stream()
                .filter(order -> allowedProperties.contains(order.getProperty()))
                .toList();

        Sort effective = allowedOrders.isEmpty() ? DEFAULT_TRANSACTION_SORT : Sort.by(allowedOrders);

        boolean alreadyStable = allowedOrders.stream()
                .anyMatch(order -> STABLE_TIEBREAKER.getProperty().equals(order.getProperty()));

        if (!alreadyStable) {
            effective = effective.and(Sort.by(STABLE_TIEBREAKER));
        }

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), effective);
    }

    /** Convenience overload for the transaction listing. */
    public static Pageable sanitizeTransactionSort(Pageable pageable) {
        return sanitize(pageable, TRANSACTION_SORT_PROPERTIES);
    }
}
