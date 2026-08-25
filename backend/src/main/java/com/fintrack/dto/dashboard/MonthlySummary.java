package com.fintrack.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Income, expenses and the net movement for one calendar month.
 *
 * <p>A {@link YearMonth} rather than a formatted string, so the value serializes as
 * "2026-08" while staying a real temporal type on the way there -- clients get a
 * sortable key and the server keeps type safety.
 *
 * @param month    the calendar month
 * @param income   total income recorded in that month
 * @param expenses total expenses recorded in that month
 * @param net      income minus expenses; negative when the month ran at a loss
 */
@Schema(description = "One month of the income and expense trend")
public record MonthlySummary(

        @Schema(description = "Calendar month", example = "2026-08", type = "string")
        YearMonth month,

        @Schema(description = "Total income in the month", example = "5000.00")
        BigDecimal income,

        @Schema(description = "Total expenses in the month", example = "3280.50")
        BigDecimal expenses,

        @Schema(description = "Income minus expenses; negative for a month that ran at a loss",
                example = "1719.50")
        BigDecimal net
) {
}
