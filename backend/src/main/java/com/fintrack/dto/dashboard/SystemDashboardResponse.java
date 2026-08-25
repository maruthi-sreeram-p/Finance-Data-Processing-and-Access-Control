package com.fintrack.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Organization-wide financial overview, for ADMIN and MANAGER.
 *
 * <p>Distinct from {@link DashboardResponse}, which always describes exactly one
 * account. The two are separate types rather than one shape with a nullable owner
 * because the difference between them is a privilege boundary: a single response
 * class would make it possible to return system-wide figures from the personal
 * endpoint by passing the wrong argument.
 *
 * <p>Note what is deliberately absent: any per-user breakdown. This reports what the
 * organization spent, not what each person spent. A manager needs the aggregate to
 * do their job; a line-by-line roster of colleagues' finances is a different and far
 * more invasive thing, and nothing in the stated requirements asks for it. Reading an
 * individual ledger remains possible but explicit -- {@code ?userId=} on the personal
 * dashboard -- so it is a deliberate act rather than an incidental disclosure.
 *
 * @param scope             always {@code "SYSTEM"}, so a client cannot confuse this
 *                          payload with a personal one
 * @param periodFrom        inclusive start, absent when unbounded
 * @param periodTo          inclusive end, absent when unbounded
 * @param totalIncome       income recorded across every account
 * @param totalExpenses     expenses recorded across every account
 * @param netPosition       income minus expenses across the organization
 * @param transactionCount  transactions in the period
 * @param activeLedgerCount how many distinct accounts recorded anything
 * @param categoryBreakdown per-category totals across all accounts, largest first
 * @param monthlySummary    month-by-month trend across all accounts, oldest first
 */
@Schema(description = "Organization-wide financial overview. Requires the ADMIN or MANAGER role.")
public record SystemDashboardResponse(

        @Schema(description = "Always SYSTEM, distinguishing this from a personal dashboard",
                example = "SYSTEM")
        String scope,

        @Schema(description = "Inclusive start of the period; absent when unbounded", example = "2026-01-01")
        LocalDate periodFrom,

        @Schema(description = "Inclusive end of the period; absent when unbounded", example = "2026-12-31")
        LocalDate periodTo,

        @Schema(description = "Income across every account", example = "482000.00")
        BigDecimal totalIncome,

        @Schema(description = "Expenses across every account", example = "311450.75")
        BigDecimal totalExpenses,

        @Schema(description = "Income minus expenses across the organization", example = "170549.25")
        BigDecimal netPosition,

        @Schema(description = "Transactions recorded in the period", example = "1284")
        long transactionCount,

        @Schema(description = "Distinct accounts that recorded at least one transaction", example = "17")
        long activeLedgerCount,

        @Schema(description = "Per-category totals across all accounts, largest first")
        List<CategoryBreakdown> categoryBreakdown,

        @Schema(description = "Month-by-month trend across all accounts, oldest first")
        List<MonthlySummary> monthlySummary
) {

    /** The only legal value of {@link #scope}. */
    public static final String SCOPE_SYSTEM = "SYSTEM";
}
