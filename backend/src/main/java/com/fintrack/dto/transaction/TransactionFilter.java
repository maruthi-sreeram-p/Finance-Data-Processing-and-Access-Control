package com.fintrack.dto.transaction;

import com.fintrack.entity.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The optional query parameters accepted by the transaction listing.
 *
 * <p>Bundled into one object rather than declared as eight separate
 * {@code @RequestParam} arguments: the controller method stays readable, the
 * constraints live next to the fields they guard, and springdoc still documents each
 * parameter individually thanks to {@code @ParameterObject}.
 *
 * <p>Every field is nullable, and null means "do not filter on this".
 *
 * @param userId     whose ledger to read; ignored for callers without cross-user
 *                   read privilege, who are always pinned to their own
 * @param type       INCOME or EXPENSE
 * @param categoryId exact category id
 * @param category   category name, matched case-insensitively; ignored if
 *                   {@code categoryId} is also present
 * @param from       inclusive lower bound on the transaction date
 * @param to         inclusive upper bound on the transaction date
 * @param minAmount  inclusive lower bound on the amount
 * @param maxAmount  inclusive upper bound on the amount
 */
@Schema(description = "Optional filters for the transaction listing. Omit a field to leave it unfiltered.")
public record TransactionFilter(

        @Schema(description = "Whose ledger to read. Only honoured for ADMIN and MANAGER; "
                + "any other caller is pinned to their own ledger.", example = "1")
        Long userId,

        @Schema(description = "Only entries of this direction", example = "EXPENSE")
        TransactionType type,

        @Schema(description = "Only entries in this category, by id", example = "3")
        Long categoryId,

        @Schema(description = "Only entries in this category, by name (case-insensitive). "
                + "Ignored when categoryId is supplied.", example = "Food")
        String category,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "Inclusive start of the date range (ISO-8601)", example = "2026-08-01")
        LocalDate from,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "Inclusive end of the date range (ISO-8601)", example = "2026-08-31")
        LocalDate to,

        @DecimalMin(value = "0.00", message = "minAmount cannot be negative")
        @Digits(integer = 17, fraction = 2, message = "minAmount must have at most 2 decimal places")
        @Schema(description = "Inclusive lower bound on amount", example = "100.00")
        BigDecimal minAmount,

        @DecimalMin(value = "0.00", message = "maxAmount cannot be negative")
        @Digits(integer = 17, fraction = 2, message = "maxAmount must have at most 2 decimal places")
        @Schema(description = "Inclusive upper bound on amount", example = "5000.00")
        BigDecimal maxAmount
) {

    /**
     * @return whether the date range is the wrong way round
     */
    public boolean hasInvertedDateRange() {
        return from != null && to != null && from.isAfter(to);
    }

    /**
     * @return whether the amount range is the wrong way round
     */
    public boolean hasInvertedAmountRange() {
        return minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0;
    }

    /**
     * @return whether a category was requested by name rather than by id
     */
    public boolean filtersByCategoryName() {
        return categoryId == null && category != null && !category.isBlank();
    }
}
