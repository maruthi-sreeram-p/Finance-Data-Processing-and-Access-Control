package com.fintrack.service.impl;

import com.fintrack.dto.dashboard.CategoryBreakdown;
import com.fintrack.dto.dashboard.DashboardResponse;
import com.fintrack.dto.dashboard.MonthlySummary;
import com.fintrack.dto.dashboard.SystemDashboardResponse;
import com.fintrack.dto.transaction.TransactionResponse;
import com.fintrack.entity.TransactionType;
import com.fintrack.entity.User;
import com.fintrack.exception.InvalidTransactionException;
import com.fintrack.mapper.TransactionMapper;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.repository.projection.CategoryTotal;
import com.fintrack.repository.projection.MonthlyTotal;
import com.fintrack.repository.projection.TypeTotal;
import com.fintrack.security.CurrentUserProvider;
import com.fintrack.security.LedgerAccessPolicy;
import com.fintrack.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the dashboard from database aggregates.
 *
 * <p>Four queries, each returning a handful of rows: totals by direction, totals by
 * category, totals by month, and the most recent entries. None of them loads the
 * ledger. The alternative -- fetching every transaction and summing in Java -- would
 * be simpler to write and would degrade in direct proportion to how long someone has
 * used the application, which is precisely the wrong shape for a feature people open
 * on every visit.
 *
 * <p>What is left in Java is only what SQL cannot express usefully: turning absent
 * rows into zeroes, pivoting direction into columns, and computing percentages.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    /** How many entries the "recent activity" panel shows. */
    private static final int RECENT_TRANSACTION_LIMIT = 5;

    /** Percentages are reported to two places, as money is. */
    private static final int PERCENTAGE_SCALE = 2;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Money is reported at two decimal places even when the total is zero. */
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

    private final TransactionRepository transactionRepository;
    private final CurrentUserProvider currentUserProvider;
    private final LedgerAccessPolicy accessPolicy;
    private final TransactionMapper transactionMapper;

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long requestedUserId, LocalDate from, LocalDate to) {
        rejectInvertedRange(from, to);

        User caller = currentUserProvider.requireCurrentUser();

        // As with the ledger listing, the policy decides whose data is read. A caller
        // without cross-user privilege silently gets their own dashboard.
        Long ownerId = accessPolicy.resolveLedgerOwner(caller.getId(), caller.getRole(), requestedUserId);

        Map<TransactionType, TypeTotal> totalsByType = indexByType(
                transactionRepository.aggregateByType(ownerId, from, to));

        BigDecimal totalIncome = totalOf(totalsByType, TransactionType.INCOME);
        BigDecimal totalExpenses = totalOf(totalsByType, TransactionType.EXPENSE);

        return new DashboardResponse(
                ownerId,
                from,
                to,
                totalIncome,
                totalExpenses,
                totalIncome.subtract(totalExpenses),
                countOf(totalsByType, TransactionType.INCOME) + countOf(totalsByType, TransactionType.EXPENSE),
                buildCategoryBreakdown(ownerId, from, to, totalIncome, totalExpenses),
                buildMonthlySummary(ownerId, from, to),
                buildRecentTransactions(ownerId));
    }

    @Override
    @Transactional(readOnly = true)
    public SystemDashboardResponse getSystemDashboard(LocalDate from, LocalDate to) {
        rejectInvertedRange(from, to);

        // No CurrentUserProvider call and no LedgerAccessPolicy consultation: this
        // method is unscoped by definition. Whether the caller may invoke it at all
        // is a role question, answered by @PreAuthorize on the controller before
        // execution reaches here.
        Map<TransactionType, TypeTotal> totalsByType =
                indexByType(transactionRepository.aggregateByTypeAcrossAllUsers(from, to));

        BigDecimal totalIncome = totalOf(totalsByType, TransactionType.INCOME);
        BigDecimal totalExpenses = totalOf(totalsByType, TransactionType.EXPENSE);

        List<CategoryBreakdown> breakdown =
                transactionRepository.aggregateByCategoryAcrossAllUsers(from, to).stream()
                        .map(row -> toCategoryBreakdown(row, totalIncome, totalExpenses))
                        .toList();

        List<MonthlySummary> monthly =
                pivotMonthly(transactionRepository.aggregateByMonthAcrossAllUsers(from, to));

        long transactionCount = countOf(totalsByType, TransactionType.INCOME)
                + countOf(totalsByType, TransactionType.EXPENSE);

        log.info("System dashboard built over {} transactions", transactionCount);

        return new SystemDashboardResponse(
                SystemDashboardResponse.SCOPE_SYSTEM,
                from,
                to,
                totalIncome,
                totalExpenses,
                totalIncome.subtract(totalExpenses),
                transactionCount,
                transactionRepository.countActiveLedgers(from, to),
                breakdown,
                monthly);
    }

    // --- Sections ------------------------------------------------------------

    private List<CategoryBreakdown> buildCategoryBreakdown(Long ownerId,
                                                           LocalDate from,
                                                           LocalDate to,
                                                           BigDecimal totalIncome,
                                                           BigDecimal totalExpenses) {

        return transactionRepository.aggregateByCategory(ownerId, from, to).stream()
                .map(row -> toCategoryBreakdown(row, totalIncome, totalExpenses))
                .toList();
    }

    private static CategoryBreakdown toCategoryBreakdown(CategoryTotal row,
                                                  BigDecimal totalIncome,
                                                  BigDecimal totalExpenses) {

        // Each share is taken against the total for its own direction. Measuring an
        // expense category against income plus expenses would make every percentage
        // shrink as unrelated income arrived.
        BigDecimal directionTotal = row.type() == TransactionType.INCOME ? totalIncome : totalExpenses;

        return new CategoryBreakdown(
                row.categoryId(),
                row.categoryName(),
                row.type(),
                row.total(),
                row.count(),
                percentageOf(row.total(), directionTotal));
    }

    /**
     * Pivots the per-month, per-direction rows into one entry per month.
     *
     * <p>A {@link LinkedHashMap} preserves the chronological order the query already
     * established, so the series does not have to be sorted again.
     */
    private List<MonthlySummary> buildMonthlySummary(Long ownerId, LocalDate from, LocalDate to) {
        return pivotMonthly(transactionRepository.aggregateByMonth(ownerId, from, to));
    }

    /**
     * Pivots per-month, per-direction rows into one entry per month.
     *
     * <p>Shared by the personal and system dashboards so the two cannot drift into
     * disagreeing about what a month's net figure means.
     */
    private static List<MonthlySummary> pivotMonthly(List<MonthlyTotal> rows) {
        Map<YearMonth, BigDecimal[]> byMonth = new LinkedHashMap<>();

        for (MonthlyTotal row : rows) {
            // Index 0 holds income, index 1 expenses.
            BigDecimal[] totals = byMonth.computeIfAbsent(
                    row.yearMonth(), month -> new BigDecimal[]{ZERO_MONEY, ZERO_MONEY});

            int slot = row.type() == TransactionType.INCOME ? 0 : 1;
            totals[slot] = totals[slot].add(row.total());
        }

        return byMonth.entrySet().stream()
                .map(entry -> {
                    BigDecimal income = entry.getValue()[0];
                    BigDecimal expenses = entry.getValue()[1];
                    return new MonthlySummary(entry.getKey(), income, expenses, income.subtract(expenses));
                })
                .toList();
    }

    private List<TransactionResponse> buildRecentTransactions(Long ownerId) {
        return transactionRepository
                .findRecentByUserId(ownerId, PageRequest.of(0, RECENT_TRANSACTION_LIMIT)).stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    // --- Helpers -------------------------------------------------------------

    private static Map<TransactionType, TypeTotal> indexByType(List<TypeTotal> rows) {
        Map<TransactionType, TypeTotal> byType = new EnumMap<>(TransactionType.class);
        rows.forEach(row -> byType.put(row.type(), row));
        return byType;
    }

    /**
     * A direction with no transactions produces no row at all, so an absent entry
     * has to become zero here. Returning null would push the same check onto every
     * consumer of the response.
     */
    private static BigDecimal totalOf(Map<TransactionType, TypeTotal> byType, TransactionType type) {
        TypeTotal row = byType.get(type);
        return row == null || row.total() == null ? ZERO_MONEY : row.total();
    }

    private static long countOf(Map<TransactionType, TypeTotal> byType, TransactionType type) {
        TypeTotal row = byType.get(type);
        return row == null || row.count() == null ? 0L : row.count();
    }

    /**
     * @return {@code part} as a percentage of {@code whole}, or zero when the whole
     *         is zero -- dividing would otherwise throw on an empty ledger
     */
    private static BigDecimal percentageOf(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(PERCENTAGE_SCALE, RoundingMode.UNNECESSARY);
        }
        // A rounding mode is mandatory: the quotient of two decimals is frequently
        // non-terminating, and BigDecimal throws rather than guessing.
        return part.multiply(HUNDRED).divide(whole, PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }

    private static void rejectInvertedRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidTransactionException("The 'from' date must not be after the 'to' date");
        }
    }
}
