package com.fintrack.mapper;

import com.fintrack.dto.category.CategoryRequest;
import com.fintrack.dto.category.CategoryResponse;
import com.fintrack.entity.Category;
import org.springframework.stereotype.Component;

/**
 * Converts between {@link Category} entities and their API representations.
 */
@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getType()
        );
    }

    public Category toEntity(CategoryRequest request) {
        return Category.builder()
                .name(request.name().trim())
                .description(trimToNull(request.description()))
                .type(request.type())
                .build();
    }

    /** Applies an update in place, leaving the id and audit fields untouched. */
    public void applyUpdate(Category category, CategoryRequest request) {
        category.setName(request.name().trim());
        category.setDescription(trimToNull(request.description()));
        category.setType(request.type());
    }

    /**
     * Stores a blank description as null rather than as an empty string, so
     * "absent" has exactly one representation in the database.
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
