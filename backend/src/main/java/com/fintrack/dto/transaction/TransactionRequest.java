package com.fintrack.dto.transaction;

import com.fintrack.entity.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Body for creating or replacing a transaction.
 *
 * <p>There is no owner field. The owner is always the authenticated caller, taken
 * from the security context -- accepting a {@code userId} here would let anyone file
 * entries into someone else's ledger, and no amount of downstream checking makes
 * that safe to accept in the first place.
 */
@Schema(description = "Create or update a transaction. The owner is always the authenticated caller.")
public record TransactionRequest(

        @NotNull(message = "Type is required")
        @Schema(description = "Direction of the money movement",
                example = "EXPENSE", requiredMode = Schema.RequiredMode.REQUIRED)
        TransactionType type,

        @NotNull(message = "Amount is required")
        // 0.01 rather than @Positive: @Positive would accept 0.001, which rounds to
        // zero at the column scale and would store a transaction worth nothing.
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 17, fraction = 2, message = "Amount must have at most 17 digits and 2 decimal places")
        @Schema(description = "Always positive; the direction is carried by type, not by a sign",
                example = "1250.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal amount,

        @NotNull(message = "Category is required")
        @Schema(description = "Id of an existing category whose type admits this transaction",
                example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        Long categoryId,

        @Size(max = 255, message = "Description must not exceed 255 characters")
        @Schema(description = "Optional free-text note", example = "August rent")
        String description,

        @NotNull(message = "Transaction date is required")
        // A ledger records money that has already moved. A future-dated entry would
        // silently distort the current balance. Scheduled and recurring transactions
        // are a separate feature, not a special case of this one.
        @PastOrPresent(message = "Transaction date cannot be in the future")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "The date the money actually moved (ISO-8601)",
                example = "2026-08-01", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate transactionDate
) {
}
