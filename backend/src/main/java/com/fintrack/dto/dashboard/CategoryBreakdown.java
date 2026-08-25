package com.fintrack.dto.dashboard;

import com.fintrack.entity.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * How much went through one category, in one direction.
 *
 * @param categoryId       id of the category
 * @param categoryName     its display name
 * @param type             the direction being summarised
 * @param total            sum of the amounts
 * @param transactionCount how many entries contributed
 * @param percentage       this total as a percentage of all activity in the same
 *                         direction, so expense shares are comparable with each
 *                         other rather than diluted by income
 */
@Schema(description = "Total for one category in one direction")
public record CategoryBreakdown(

        @Schema(description = "Category id", example = "3")
        Long categoryId,

        @Schema(description = "Category name", example = "Rent")
        String categoryName,

        @Schema(description = "Direction summarised", example = "EXPENSE")
        TransactionType type,

        @Schema(description = "Sum of the amounts", example = "14400.00")
        BigDecimal total,

        @Schema(description = "Number of contributing transactions", example = "12")
        long transactionCount,

        @Schema(description = "Share of all activity in the same direction, as a percentage",
                example = "34.01")
        BigDecimal percentage
) {
}
