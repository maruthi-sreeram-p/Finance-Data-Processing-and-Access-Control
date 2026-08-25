package com.fintrack.dto.transaction;

import com.fintrack.dto.category.CategoryResponse;
import com.fintrack.entity.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A transaction as exposed by the API.
 *
 * <p>The category is nested rather than reduced to an id: a client rendering a list
 * needs the name, and returning only ids would force it to fetch and join the
 * category table itself on every page.
 *
 * <p>The owner is identified by id alone. Administrators and managers can read
 * across users, and including the owner's name or email here would spread personal
 * data through every ledger response for no benefit -- the account endpoints already
 * provide it to the callers entitled to see it.
 */
@Schema(description = "A single income or expense entry")
public record TransactionResponse(

        @Schema(description = "Unique identifier", example = "101")
        Long id,

        @Schema(description = "Id of the account this entry belongs to", example = "1")
        Long userId,

        @Schema(description = "Direction of the money movement", example = "EXPENSE")
        TransactionType type,

        @Schema(description = "Always positive; direction is carried by type", example = "1250.00")
        BigDecimal amount,

        @Schema(description = "The category this entry is filed under")
        CategoryResponse category,

        @Schema(description = "Free-text note", example = "August rent")
        String description,

        @Schema(description = "The date the money actually moved", example = "2026-08-01")
        LocalDate transactionDate,

        @Schema(description = "When the record was created")
        Instant createdAt,

        @Schema(description = "When the record was last modified")
        Instant updatedAt
) {
}
