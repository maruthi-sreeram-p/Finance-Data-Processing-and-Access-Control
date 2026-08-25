package com.fintrack.dto.dashboard;

import com.fintrack.dto.transaction.TransactionResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The financial overview for one account.
 *
 * <p>The four headline figures are top level, so the common case -- a client that
 * only wants to show a balance -- reads them directly rather than digging through a
 * nested summary object. The analytical sections follow, each independently useful
 * and each safely ignorable.
 *
 * @param userId           whose dashboard this is
 * @param periodFrom       inclusive start of the period, null when unbounded
 * @param periodTo         inclusive end of the period, null when unbounded
 * @param totalIncome      sum of all income in the period
 * @param totalExpenses    sum of all expenses in the period
 * @param balance          {@code totalIncome - totalExpenses}; negative when
 *                         spending exceeded income
 * @param transactionCount how many transactions the period contains
 * @param categoryBreakdown per-category totals, largest first
 * @param monthlySummary   month-by-month trend, oldest first
 * @param recentTransactions the most recent entries, newest first
 */
@Schema(description = "Financial overview: headline totals plus category, monthly and recent detail")
public record DashboardResponse(

        @Schema(description = "Id of the account this dashboard describes", example = "1")
        Long userId,

        @Schema(description = "Inclusive start of the period; absent when unbounded", example = "2026-01-01")
        LocalDate periodFrom,

        @Schema(description = "Inclusive end of the period; absent when unbounded", example = "2026-12-31")
        LocalDate periodTo,

        @Schema(description = "Total income in the period", example = "75000.00")
        BigDecimal totalIncome,

        @Schema(description = "Total expenses in the period", example = "42350.00")
        BigDecimal totalExpenses,

        @Schema(description = "Income minus expenses; negative when spending exceeded income",
                example = "32650.00")
        BigDecimal balance,

        @Schema(description = "Number of transactions in the period", example = "42")
        long transactionCount,

        @Schema(description = "Per-category totals, largest first")
        List<CategoryBreakdown> categoryBreakdown,

        @Schema(description = "Month-by-month trend, oldest first")
        List<MonthlySummary> monthlySummary,

        @Schema(description = "The most recent transactions, newest first")
        List<TransactionResponse> recentTransactions
) {
}
