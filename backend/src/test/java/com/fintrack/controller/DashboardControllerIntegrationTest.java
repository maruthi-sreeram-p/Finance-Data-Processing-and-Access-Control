package com.fintrack.controller;

import com.fintrack.entity.Category;
import com.fintrack.entity.Role;
import com.fintrack.entity.Transaction;
import com.fintrack.entity.TransactionType;
import com.fintrack.entity.User;
import com.fintrack.support.AbstractIntegrationTest;
import com.fintrack.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dashboard aggregation, verified against a ledger whose correct answers are known
 * by hand.
 *
 * <p>Alice's ledger below sums to income 5000.00 and expenses 1325.50, so a balance
 * of 3674.50 over 4 transactions. Bob owns entries too, which is what makes the
 * scoping assertions meaningful -- an aggregate query that forgot its {@code WHERE
 * user_id} clause would produce visibly wrong totals rather than passing quietly.
 */
class DashboardControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String DASHBOARD_URL = "/api/dashboard";

    private User alice;
    private User bob;
    private User manager;

    private String aliceToken;
    private String bobToken;
    private String managerToken;

    @BeforeEach
    void setUp() {
        alice = persistUser("alice@fintrack.test", Role.USER);
        bob = persistUser("bob@fintrack.test", Role.USER);
        manager = persistUser("manager@fintrack.test", Role.MANAGER);

        aliceToken = tokenFor(alice);
        bobToken = tokenFor(bob);
        managerToken = tokenFor(manager);

        Category rent = category("Rent");
        Category food = category("Food");
        Category salary = category("Salary");

        // Alice: 5000.00 income, 1325.50 expenses across two months.
        save(alice, salary, TransactionType.INCOME, "5000.00", LocalDate.of(2026, 7, 31));
        save(alice, rent, TransactionType.EXPENSE, "1200.00", LocalDate.of(2026, 8, 1));
        save(alice, food, TransactionType.EXPENSE, "80.00", LocalDate.of(2026, 8, 15));
        save(alice, food, TransactionType.EXPENSE, "45.50", LocalDate.of(2026, 8, 20));

        // Bob's ledger must never appear in Alice's figures.
        save(bob, rent, TransactionType.EXPENSE, "999.99", LocalDate.of(2026, 8, 1));
    }

    @Nested
    @DisplayName("headline totals")
    class HeadlineTotals {

        @Test
        @DisplayName("sums income and expenses and derives the balance")
        void computesTotals() throws Exception {
            mockMvc.perform(get(DASHBOARD_URL).header("Authorization", bearer(aliceToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(alice.getId()))
                    .andExpect(jsonPath("$.totalIncome").value(5000.00))
                    .andExpect(jsonPath("$.totalExpenses").value(1325.50))
                    // balance = income - expenses
                    .andExpect(jsonPath("$.balance").value(3674.50))
                    .andExpect(jsonPath("$.transactionCount").value(4));
        }

        @Test
        @DisplayName("counts only the caller's own ledger")
        void scopesTotalsToOwner() throws Exception {
            // Bob owns exactly one entry. If the aggregates leaked across users his
            // expenses would come back as 2325.49.
            mockMvc.perform(get(DASHBOARD_URL).header("Authorization", bearer(bobToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalExpenses").value(999.99))
                    .andExpect(jsonPath("$.totalIncome").value(0))
                    .andExpect(jsonPath("$.transactionCount").value(1));
        }

        @Test
        @DisplayName("returns zeroes rather than nulls for an empty ledger")
        void handlesEmptyLedger() throws Exception {
            User newcomer = persistUser("newcomer@fintrack.test", Role.USER);

            // A direction with no rows produces no aggregate row at all, so this is
            // the case where a missing coalesce would surface as null.
            mockMvc.perform(get(DASHBOARD_URL).header("Authorization", bearer(tokenFor(newcomer))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalIncome").value(0))
                    .andExpect(jsonPath("$.totalExpenses").value(0))
                    .andExpect(jsonPath("$.balance").value(0))
                    .andExpect(jsonPath("$.transactionCount").value(0))
                    .andExpect(jsonPath("$.categoryBreakdown").isEmpty())
                    .andExpect(jsonPath("$.recentTransactions").isEmpty());
        }

        @Test
        @DisplayName("reports a negative balance when spending exceeded income")
        void reportsNegativeBalance() throws Exception {
            mockMvc.perform(get(DASHBOARD_URL).header("Authorization", bearer(bobToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(-999.99));
        }

        @Test
        @DisplayName("requires authentication")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get(DASHBOARD_URL))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("period filtering")
    class PeriodFiltering {

        @Test
        @DisplayName("restricts totals to an inclusive date range")
        void filtersByPeriod() throws Exception {
            // August only: the 31 July salary drops out, all three expenses remain.
            mockMvc.perform(get(DASHBOARD_URL)
                            .header("Authorization", bearer(aliceToken))
                            .param("from", "2026-08-01")
                            .param("to", "2026-08-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalIncome").value(0))
                    .andExpect(jsonPath("$.totalExpenses").value(1325.50))
                    .andExpect(jsonPath("$.balance").value(-1325.50))
                    .andExpect(jsonPath("$.transactionCount").value(3))
                    .andExpect(jsonPath("$.periodFrom").value("2026-08-01"))
                    .andExpect(jsonPath("$.periodTo").value("2026-08-31"));
        }

        @Test
        @DisplayName("includes transactions falling exactly on either boundary")
        void boundariesAreInclusive() throws Exception {
            mockMvc.perform(get(DASHBOARD_URL)
                            .header("Authorization", bearer(aliceToken))
                            .param("from", "2026-07-31")
                            .param("to", "2026-08-01"))
                    .andExpect(status().isOk())
                    // 31 July salary and 1 August rent: both endpoints count.
                    .andExpect(jsonPath("$.transactionCount").value(2))
                    .andExpect(jsonPath("$.totalIncome").value(5000.00))
                    .andExpect(jsonPath("$.totalExpenses").value(1200.00));
        }

        @Test
        @DisplayName("rejects an inverted range")
        void rejectsInvertedRange() throws Exception {
            mockMvc.perform(get(DASHBOARD_URL)
                            .header("Authorization", bearer(aliceToken))
                            .param("from", "2026-08-31")
                            .param("to", "2026-08-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_TRANSACTION"));
        }
    }

    @Nested
    @DisplayName("category breakdown")
    class Breakdown {

        @Test
        @DisplayName("groups by category and orders by total descending")
        void ordersByTotalDescending() throws Exception {
            mockMvc.perform(get(DASHBOARD_URL).header("Authorization", bearer(aliceToken)))
                    .andExpect(status().isOk())
                    // Salary 5000.00, then Rent 1200.00, then Food 125.50 (two entries merged).
                    .andExpect(jsonPath("$.categoryBreakdown[0].categoryName").value("Salary"))
                    .andExpect(jsonPath("$.categoryBreakdown[0].total").value(5000.00))
                    .andExpect(jsonPath("$.categoryBreakdown[1].categoryName").value("Rent"))
                    .andExpect(jsonPath("$.categoryBreakdown[1].total").value(1200.00))
                    .andExpect(jsonPath("$.categoryBreakdown[2].categoryName").value("Food"))
                    .andExpect(jsonPath("$.categoryBreakdown[2].total").value(125.50))
                    .andExpect(jsonPath("$.categoryBreakdown[2].transactionCount").value(2));
        }

        @Test
        @DisplayName("expresses each share against its own direction, not the combined total")
        void percentagesAreRelativeToDirection() throws Exception {
            mockMvc.perform(get(DASHBOARD_URL).header("Authorization", bearer(aliceToken)))
                    .andExpect(status().isOk())
                    // Salary is the only income, so 100% of income -- not 79% of everything.
                    .andExpect(jsonPath("$.categoryBreakdown[0].percentage").value(100.00))
                    // Rent is 1200.00 of 1325.50 expenses = 90.53%.
                    .andExpect(jsonPath("$.categoryBreakdown[1].percentage").value(90.53))
                    // Food is 125.50 of 1325.50 = 9.47%. The two expense shares total 100.
                    .andExpect(jsonPath("$.categoryBreakdown[2].percentage").value(9.47));
        }
    }

    @Nested
    @DisplayName("monthly summary and recent activity")
    class MonthlyAndRecent {

        @Test
        @DisplayName("pivots each month into income, expenses and net, oldest first")
        void buildsMonthlyTrend() throws Exception {
            mockMvc.perform(get(DASHBOARD_URL).header("Authorization", bearer(aliceToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.monthlySummary.length()").value(2))
                    .andExpect(jsonPath("$.monthlySummary[0].month").value("2026-07"))
                    .andExpect(jsonPath("$.monthlySummary[0].income").value(5000.00))
                    .andExpect(jsonPath("$.monthlySummary[0].expenses").value(0))
                    .andExpect(jsonPath("$.monthlySummary[0].net").value(5000.00))
                    .andExpect(jsonPath("$.monthlySummary[1].month").value("2026-08"))
                    .andExpect(jsonPath("$.monthlySummary[1].income").value(0))
                    .andExpect(jsonPath("$.monthlySummary[1].expenses").value(1325.50))
                    // A month that ran at a loss reports a negative net.
                    .andExpect(jsonPath("$.monthlySummary[1].net").value(-1325.50));
        }

        @Test
        @DisplayName("lists the most recent entries newest first")
        void listsRecentTransactions() throws Exception {
            mockMvc.perform(get(DASHBOARD_URL).header("Authorization", bearer(aliceToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recentTransactions.length()").value(4))
                    .andExpect(jsonPath("$.recentTransactions[0].transactionDate").value("2026-08-20"))
                    .andExpect(jsonPath("$.recentTransactions[3].transactionDate").value("2026-07-31"))
                    // Recent activity is the owner's, never anybody else's.
                    .andExpect(jsonPath("$.recentTransactions[0].userId").value(alice.getId()));
        }
    }

    @Nested
    @DisplayName("cross-user access")
    class CrossUserAccess {

        @Test
        @DisplayName("MANAGER may read another account's dashboard")
        void managerMayReadAnotherDashboard() throws Exception {
            mockMvc.perform(get(DASHBOARD_URL)
                            .header("Authorization", bearer(managerToken))
                            .param("userId", String.valueOf(alice.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(alice.getId()))
                    .andExpect(jsonPath("$.totalIncome").value(5000.00));
        }

        @Test
        @DisplayName("a plain user asking for another dashboard silently gets their own")
        void plainUserIsPinnedToOwnDashboard() throws Exception {
            mockMvc.perform(get(DASHBOARD_URL)
                            .header("Authorization", bearer(bobToken))
                            .param("userId", String.valueOf(alice.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(bob.getId()))
                    // Alice's 5000.00 income must not appear here.
                    .andExpect(jsonPath("$.totalIncome").value(0))
                    .andExpect(jsonPath("$.totalExpenses").value(999.99));
        }

        @Test
        @DisplayName("a privileged caller with no userId sees their own dashboard")
        void privilegedCallerDefaultsToOwn() throws Exception {
            mockMvc.perform(get(DASHBOARD_URL).header("Authorization", bearer(managerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(manager.getId()))
                    .andExpect(jsonPath("$.transactionCount").value(0));
        }
    }

    // --- Helpers -------------------------------------------------------------

    private Category category(String name) {
        return categoryRepository.findByNameIgnoreCase(name).orElseThrow();
    }

    private Transaction save(User owner, Category category, TransactionType type,
                             String amount, LocalDate date) {
        Transaction transaction = TestDataFactory.transaction(owner, category, type, amount);
        transaction.setTransactionDate(date);
        return transactionRepository.save(transaction);
    }
}
