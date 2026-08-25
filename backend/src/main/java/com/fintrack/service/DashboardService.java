package com.fintrack.service;

import com.fintrack.dto.dashboard.DashboardResponse;
import com.fintrack.dto.dashboard.SystemDashboardResponse;

import java.time.LocalDate;

/**
 * The financial overview.
 *
 * <p>Read-only by construction: nothing here mutates state, so the whole service
 * can run in a read-only transaction and can later be cached or served from a
 * replica without changing any caller.
 */
public interface DashboardService {

    /**
     * Builds the dashboard for one account.
     *
     * @param requestedUserId whose dashboard to build; honoured only for callers
     *                        with cross-user read privilege, and otherwise ignored
     *                        in favour of the caller's own account
     * @param from            inclusive start of the period, or null for unbounded
     * @param to              inclusive end of the period, or null for unbounded
     * @throws com.fintrack.exception.InvalidTransactionException if the range is inverted
     */
    DashboardResponse getDashboard(Long requestedUserId, LocalDate from, LocalDate to);

    /**
     * Builds the organization-wide overview, aggregated across every account.
     *
     * <p>Carries no role check of its own. Authorization for this operation is a
     * coarse role question with no resource to inspect, so it belongs on the
     * controller as {@code @PreAuthorize} where a reader can see it next to the
     * route -- unlike ownership, which can only be decided here.
     *
     * @throws com.fintrack.exception.InvalidTransactionException if the range is inverted
     */
    SystemDashboardResponse getSystemDashboard(LocalDate from, LocalDate to);
}
