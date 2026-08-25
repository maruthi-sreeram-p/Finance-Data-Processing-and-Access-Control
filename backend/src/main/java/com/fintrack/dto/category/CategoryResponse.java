package com.fintrack.dto.category;

import com.fintrack.entity.CategoryType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A category as exposed by the API.
 *
 * <p>Nested inside every transaction response so a client can render "Rent" without
 * a second request, and can populate a category picker from the same shape it
 * receives back.
 */
@Schema(description = "A transaction category")
public record CategoryResponse(

        @Schema(description = "Unique identifier", example = "3")
        Long id,

        @Schema(description = "Display name", example = "Rent")
        String name,

        @Schema(description = "What the category covers", example = "Rent, mortgage and housing charges")
        String description,

        @Schema(description = "Which transaction directions may use this category", example = "EXPENSE")
        CategoryType type
) {
}
