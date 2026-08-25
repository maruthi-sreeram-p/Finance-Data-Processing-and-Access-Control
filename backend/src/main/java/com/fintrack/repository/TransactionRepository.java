package com.fintrack.repository;

import com.fintrack.entity.Transaction;
import com.fintrack.repository.projection.CategoryTotal;
import com.fintrack.repository.projection.MonthlyTotal;
import com.fintrack.repository.projection.TypeTotal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Data access for the transaction ledger.
 *
 * <p>Filtering is expressed with {@link Specification} rather than with a
 * combinatorial explosion of derived query methods: with four optional filters
 * (type, category, from, to) a derived-method approach would need sixteen
 * signatures, whereas a specification composes them at runtime.
 */
@Repository
public interface TransactionRepository
        extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    /**
     * Loads a page of transactions matching the given specification, pre-fetching
     * the associations the response DTO needs.
     *
     * <p>The override exists purely to attach the entity graph. Without it, mapping
     * a 20-row page would trigger 40 additional selects -- the classic N+1 -- because
     * both associations are lazy. Both are {@code @ManyToOne}, so the fetch join does
     * not multiply rows and pagination stays in the database.
     */
    @Override
    @EntityGraph(attributePaths = {"category", "user"})
    Page<Transaction> findAll(Specification<Transaction> specification, Pageable pageable);

    /**
     * Ownership-scoped lookup: returns the transaction only when it belongs to the
     * given user.
     *
     * <p>Folding the owner into the {@code WHERE} clause, instead of loading by id
     * and comparing afterwards, means a mismatch is indistinguishable from a missing
     * row. That is what stops {@code GET /api/transactions/11} from confirming to
     * user A that transaction 11 exists but belongs to someone else.
     */
    @EntityGraph(attributePaths = {"category", "user"})
    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    /** Unscoped lookup, for the elevated code paths that have already been authorized. */
    @EntityGraph(attributePaths = {"category", "user"})
    Optional<Transaction> findWithDetailsById(Long id);

    // --- Dashboard aggregates -------------------------------------------------
    //
    // These sum in the database rather than in Java. Loading a ledger into heap to
    // add it up would allocate proportionally to history size and get slower every
    // month; a GROUP BY over the (user_id, transaction_date) index returns a
    // handful of rows whatever the ledger contains.
    //
    // Each date bound is optional, expressed as ":param is null or ...". Hibernate
    // folds the comparison away when the parameter is null, so one query serves
    // both the all-time and the date-bounded dashboard.

    /**
     * Totals and counts per direction: two rows at most.
     *
     * <p>A direction with no transactions yields no row at all rather than a zero,
     * so callers must treat an absent row as zero.
     */
    @Query("""
            select new com.fintrack.repository.projection.TypeTotal(t.type, sum(t.amount), count(t))
            from Transaction t
            where t.user.id = :userId
              and (:from is null or t.transactionDate >= :from)
              and (:to is null or t.transactionDate <= :to)
            group by t.type
            """)
    List<TypeTotal> aggregateByType(@Param("userId") Long userId,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);

    /**
     * Totals per category and direction, largest first.
     *
     * <p>Ordered in the database so that "top spending categories" is a matter of
     * taking the first few rows rather than sorting a list in the service.
     */
    @Query("""
            select new com.fintrack.repository.projection.CategoryTotal(
                       c.id, c.name, t.type, sum(t.amount), count(t))
            from Transaction t
            join t.category c
            where t.user.id = :userId
              and (:from is null or t.transactionDate >= :from)
              and (:to is null or t.transactionDate <= :to)
            group by c.id, c.name, t.type
            order by sum(t.amount) desc
            """)
    List<CategoryTotal> aggregateByCategory(@Param("userId") Long userId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    /**
     * Totals per calendar month and direction, oldest first.
     *
     * <p>EXTRACT is used rather than Hibernate's {@code year()}/{@code month()}
     * shorthands because it is standard JPQL and renders correctly on both MySQL and
     * H2, which is what lets the test suite exercise this query at all.
     */
    @Query("""
            select new com.fintrack.repository.projection.MonthlyTotal(
                       extract(year from t.transactionDate),
                       extract(month from t.transactionDate),
                       t.type,
                       sum(t.amount))
            from Transaction t
            where t.user.id = :userId
              and (:from is null or t.transactionDate >= :from)
              and (:to is null or t.transactionDate <= :to)
            group by extract(year from t.transactionDate), extract(month from t.transactionDate), t.type
            order by extract(year from t.transactionDate), extract(month from t.transactionDate)
            """)
    List<MonthlyTotal> aggregateByMonth(@Param("userId") Long userId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);

    /**
     * The most recent entries for the dashboard panel.
     *
     * <p>Ordered by id as well as date so the selection is deterministic when
     * several entries share a date; without the tiebreaker the "recent" list could
     * differ between two identical requests.
     */
    @EntityGraph(attributePaths = {"category", "user"})
    @Query("""
            select t
            from Transaction t
            where t.user.id = :userId
            order by t.transactionDate desc, t.id desc
            """)
    List<Transaction> findRecentByUserId(@Param("userId") Long userId, Pageable pageable);

    // --- System-wide aggregates -----------------------------------------------
    //
    // The same three roll-ups without the owner predicate, for the organization-wide
    // dashboard available to ADMIN and MANAGER.
    //
    // Deliberately separate methods rather than making `userId` nullable on the
    // queries above. A nullable owner would mean one careless `null` silently turns
    // a personal dashboard into a system-wide data leak; here the unscoped variants
    // are named, greppable, and reachable only from a role-guarded service method.

    @Query("""
            select new com.fintrack.repository.projection.TypeTotal(t.type, sum(t.amount), count(t))
            from Transaction t
            where (:from is null or t.transactionDate >= :from)
              and (:to is null or t.transactionDate <= :to)
            group by t.type
            """)
    List<TypeTotal> aggregateByTypeAcrossAllUsers(@Param("from") LocalDate from,
                                                  @Param("to") LocalDate to);

    @Query("""
            select new com.fintrack.repository.projection.CategoryTotal(
                       c.id, c.name, t.type, sum(t.amount), count(t))
            from Transaction t
            join t.category c
            where (:from is null or t.transactionDate >= :from)
              and (:to is null or t.transactionDate <= :to)
            group by c.id, c.name, t.type
            order by sum(t.amount) desc
            """)
    List<CategoryTotal> aggregateByCategoryAcrossAllUsers(@Param("from") LocalDate from,
                                                          @Param("to") LocalDate to);

    @Query("""
            select new com.fintrack.repository.projection.MonthlyTotal(
                       extract(year from t.transactionDate),
                       extract(month from t.transactionDate),
                       t.type,
                       sum(t.amount))
            from Transaction t
            where (:from is null or t.transactionDate >= :from)
              and (:to is null or t.transactionDate <= :to)
            group by extract(year from t.transactionDate), extract(month from t.transactionDate), t.type
            order by extract(year from t.transactionDate), extract(month from t.transactionDate)
            """)
    List<MonthlyTotal> aggregateByMonthAcrossAllUsers(@Param("from") LocalDate from,
                                                      @Param("to") LocalDate to);

    /**
     * How many distinct accounts recorded a transaction in the period.
     *
     * <p>Reported instead of any per-user breakdown: it answers "how much of the
     * organization is actually using this" without turning an aggregate report into
     * a roster of who spent what.
     */
    @Query("""
            select count(distinct t.user.id)
            from Transaction t
            where (:from is null or t.transactionDate >= :from)
              and (:to is null or t.transactionDate <= :to)
            """)
    long countActiveLedgers(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Guards category deletion: a category still in use must not be removed. */
    boolean existsByCategoryId(Long categoryId);

    /** Guards account deletion and supports admin reporting. */
    long countByUserId(Long userId);

    void deleteByUserId(Long userId);
}
