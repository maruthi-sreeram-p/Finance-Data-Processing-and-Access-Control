package com.fintrack.controller;

import com.fintrack.dto.common.ApiError;
import com.fintrack.dto.dashboard.DashboardResponse;
import com.fintrack.dto.dashboard.SystemDashboardResponse;
import com.fintrack.entity.Role;
import com.fintrack.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * The financial overview.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Aggregated totals, category and monthly analysis")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(
            summary = "Get the financial overview",
            description = """
                    Returns total income, total expenses, the resulting balance and the \
                    transaction count, together with a per-category breakdown, a month-by-month \
                    trend and the most recent entries.

                    Covers the whole ledger unless ?from= and/or ?to= narrow it; both bounds \
                    are inclusive.

                    ADMIN and MANAGER may read another account with ?userId=. Any other caller \
                    receives their own overview regardless of what they pass.

                    Every figure is computed by an aggregate query in the database, so the \
                    response cost does not grow with the size of the ledger."""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The overview"),
            @ApiResponse(responseCode = "400", description = "An inverted date range",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<DashboardResponse> getDashboard(

            @Parameter(description = "Whose overview to return. Only honoured for ADMIN and MANAGER.")
            @RequestParam(required = false) Long userId,

            @Parameter(description = "Inclusive start of the period (ISO-8601)", example = "2026-01-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @Parameter(description = "Inclusive end of the period (ISO-8601)", example = "2026-12-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return ResponseEntity.ok(dashboardService.getDashboard(userId, from, to));
    }

    /**
     * Organization-wide overview.
     *
     * <p>The role gate lives here rather than in the service because this is a pure
     * role question -- there is no resource to inspect, so nothing about it needs the
     * data layer. Ownership is the opposite case and stays in the service, where the
     * record and the caller are both in scope. Keeping the two kinds of check in the
     * places that can actually answer them is the whole design.
     *
     * <p>{@code hasAnyRole} rather than a second endpoint per role: ADMIN and MANAGER
     * get identical output here, and duplicating the route would mean two things to
     * keep in step.
     */
    @GetMapping("/system")
    @PreAuthorize("hasAnyRole('" + Role.Names.ADMIN + "', '" + Role.Names.MANAGER + "')")
    @Operation(
            summary = "Get the organization-wide overview",
            description = """
                    Totals, category breakdown and monthly trend aggregated across every \
                    account, for the period given by ?from= and ?to=.

                    Requires the ADMIN or MANAGER role. A USER receives 403: this is \
                    system-wide financial data, which an ordinary account may never read.

                    Reports no per-user breakdown. It says what the organization spent, \
                    not what each person spent. Reading one person's ledger is still \
                    possible for these roles, but only as a deliberate act through \
                    GET /api/dashboard?userId=."""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The organization-wide overview"),
            @ApiResponse(responseCode = "400", description = "An inverted date range",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated but not ADMIN or MANAGER",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<SystemDashboardResponse> getSystemDashboard(

            @Parameter(description = "Inclusive start of the period (ISO-8601)", example = "2026-01-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @Parameter(description = "Inclusive end of the period (ISO-8601)", example = "2026-12-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return ResponseEntity.ok(dashboardService.getSystemDashboard(from, to));
    }
}
