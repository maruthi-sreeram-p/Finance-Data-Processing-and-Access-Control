package com.fintrack.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sort whitelisting and pagination stability.
 *
 * <p>{@code ?sort=} is bound straight into a JPA property path, which makes this
 * small class the boundary between a client-supplied string and the generated SQL.
 */
class SortSanitizerTest {

    @Test
    @DisplayName("keeps a whitelisted property and its direction")
    void keepsAllowedProperty() {
        Pageable sanitized = SortSanitizer.sanitizeTransactionSort(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "amount")));

        assertThat(sanitized.getSort().getOrderFor("amount")).isNotNull();
        assertThat(sanitized.getSort().getOrderFor("amount").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("drops a property that is not whitelisted rather than failing the request")
    void dropsDisallowedProperty() {
        // "user.passwordHash" is a valid JPA path. Left alone it would let a client
        // order rows by a hash and binary-search its content across requests.
        Pageable sanitized = SortSanitizer.sanitizeTransactionSort(
                PageRequest.of(0, 20, Sort.by("user.passwordHash")));

        assertThat(sanitized.getSort().getOrderFor("user.passwordHash")).isNull();
    }

    @Test
    @DisplayName("falls back to most-recent-first when nothing usable was requested")
    void fallsBackToDefaultSort() {
        Pageable sanitized = SortSanitizer.sanitizeTransactionSort(
                PageRequest.of(0, 20, Sort.by("somethingUnknown")));

        Sort.Order primary = sanitized.getSort().getOrderFor("transactionDate");
        assertThat(primary).isNotNull();
        assertThat(primary.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("applies the default when no sort was given at all")
    void appliesDefaultWhenUnsorted() {
        Pageable sanitized = SortSanitizer.sanitizeTransactionSort(PageRequest.of(0, 20));

        assertThat(sanitized.getSort().getOrderFor("transactionDate")).isNotNull();
    }

    @Test
    @DisplayName("always appends an id tiebreaker so paging cannot repeat or skip rows")
    void appendsStableTiebreaker() {
        // Without a unique final key, rows sharing a sort value have no defined order
        // between queries, and a paginated scan can return one row twice while
        // omitting another entirely.
        Pageable sanitized = SortSanitizer.sanitizeTransactionSort(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "amount")));

        assertThat(sanitized.getSort().getOrderFor("id")).isNotNull();
        assertThat(sanitized.getSort().stream().toList()).last()
                .satisfies(order -> assertThat(order.getProperty()).isEqualTo("id"));
    }

    @Test
    @DisplayName("does not append a second id order when the client already sorted by id")
    void doesNotDuplicateTiebreaker() {
        Pageable sanitized = SortSanitizer.sanitizeTransactionSort(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "id")));

        assertThat(sanitized.getSort().stream().filter(order -> "id".equals(order.getProperty())))
                .hasSize(1);
        // The client's ascending direction wins; the tiebreaker must not flip it.
        assertThat(sanitized.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("preserves a multi-property sort in the order requested")
    void preservesMultiPropertyOrder() {
        Pageable sanitized = SortSanitizer.sanitizeTransactionSort(
                PageRequest.of(0, 20, Sort.by(Sort.Order.desc("type"), Sort.Order.asc("amount"))));

        assertThat(sanitized.getSort().stream().map(Sort.Order::getProperty).toList())
                .containsExactly("type", "amount", "id");
    }

    @Test
    @DisplayName("keeps the allowed half of a partly invalid sort")
    void keepsAllowedHalfOfMixedSort() {
        Pageable sanitized = SortSanitizer.sanitizeTransactionSort(
                PageRequest.of(0, 20, Sort.by(Sort.Order.asc("amount"), Sort.Order.asc("user.email"))));

        assertThat(sanitized.getSort().stream().map(Sort.Order::getProperty).toList())
                .containsExactly("amount", "id");
    }

    @Test
    @DisplayName("leaves the page number and size untouched")
    void preservesPaginationParameters() {
        Pageable sanitized = SortSanitizer.sanitizeTransactionSort(
                PageRequest.of(3, 50, Sort.by("nonsense")));

        assertThat(sanitized.getPageNumber()).isEqualTo(3);
        assertThat(sanitized.getPageSize()).isEqualTo(50);
    }
}
