package com.fintrack.dto.category;

import com.fintrack.entity.CategoryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for creating or replacing a category. Administrator only.
 *
 * <p>Categories are reference data shared by every user, which is why changing them
 * is an administrative operation: a rename or a type change is visible to everyone
 * and can invalidate transactions that already reference the category.
 */
@Schema(description = "Create or update a transaction category")
public record CategoryRequest(

        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 60, message = "Name must be between 2 and 60 characters")
        @Schema(description = "Display name; must be unique, case-insensitively",
                example = "Subscriptions", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Size(max = 255, message = "Description must not exceed 255 characters")
        @Schema(description = "What the category covers", example = "Recurring software and media subscriptions")
        String description,

        @NotNull(message = "Type is required")
        @Schema(description = "Which transaction directions may use this category. "
                + "BOTH allows either, for categories such as Investment.",
                example = "EXPENSE", requiredMode = Schema.RequiredMode.REQUIRED)
        CategoryType type
) {
}
